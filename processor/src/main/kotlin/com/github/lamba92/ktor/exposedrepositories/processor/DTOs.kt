package com.github.lamba92.ktor.exposedrepositories.processor

import com.github.lamba92.ktor.exposedrepositories.processor.queries.*
import com.squareup.kotlinpoet.*
import kotlinx.serialization.Serializable

context(LoggerContext)
fun Sequence<TableDeclaration>.generateDTOSpecs(): List<DTOSpecs.WithFunctions> {
    val tableLookupMap = associateBy { it.className.canonicalName }
    val map = mutableMapOf<TableDeclaration, DTOSpecs.WithFunctions>()
    // get the raw column declarations
    val columnDeclarationMap = associateWith { it.getDeclaredColumn().toList() }
    // we process first all the tables without a @Reference and queue the rest.
    var (next, queue) = columnDeclarationMap
        .partition { _, value -> value.none { it is ColumnDeclaration.WithReference } }
    while (next.isNotEmpty()) {
        val newDTOSpecs = next
            .mapNotNull { (tableDeclaration, columnDeclarations) ->
                val dtoProperties = columnDeclarations.map {
                    when (it) {
                        is ColumnDeclaration.Simple -> it.generateDTOPropertiesSpecs()
                        is ColumnDeclaration.WithReference -> {
                            val referencedTableDeclaration = tableLookupMap
                                .getValue(it.reference.tableClassName.canonicalName)
                            it.generateDTOPropertiesSpecs(
                                referencedDtoSpec = map.getValue(referencedTableDeclaration).specs,
                                referencedTableDeclaration = referencedTableDeclaration
                            )
                        }
                    }
                }
                if (dtoProperties.isEmpty()) return@mapNotNull null
                val generatedPackageName = tableDeclaration.declaration.packageName.asString()
                val dtoClassName = ClassName(
                    packageName = "$generatedPackageName.dto",
                    tableDeclaration.names.singular
                )
                val updateQueryDtoClassName = ClassName(
                    packageName = "$generatedPackageName.dto",
                    "${tableDeclaration.names.plural}UpdateQuery"
                )
                val dtoSpecs = generateDtos(tableDeclaration, dtoClassName, updateQueryDtoClassName, dtoProperties)
                val insertSingle = generateSingleInsert(dtoSpecs, map)
                val selectSpecs = dtoProperties
                    .filterIsInstance<DTOProperty.Simple>()
                    .associate {
                        val generateSelectBySingleProperty = generateSelectBySingleProperty(dtoSpecs, it, map)
                        it.declarationSimpleName to Pair(
                            generateSelectBySingleProperty,
                            generateSelectByMultipleProperties(dtoSpecs, it, generateSelectBySingleProperty)
                        )
                    }
                val update = dtoProperties
                    .filterIsInstance<DTOProperty.Simple>()
                    .associate {
                        it.declarationSimpleName to generateUpdateBySingleProperty(
                            dtoSpecs = dtoSpecs,
                            dtoProperty = it,
                            map = map
                        )
                    }
                val delete = dtoProperties
                    .filterIsInstance<DTOProperty.Simple>()
                    .associate {
                        it.declarationSimpleName to generateDeleteBySingleProperty(it, dtoSpecs)
                    }
                val generatedQueries = GeneratedQueryFunctions(
                    insertSingle = insertSingle,
                    insertBulk = generateBulkInsert(dtoSpecs, insertSingle),
                    selectBySingle = selectSpecs.mapValues { it.value.first },
                    selectByMultiple = selectSpecs.mapValues { it.value.second },
                    delete = delete,
                    update = update
                )
                tableDeclaration to DTOSpecs.WithFunctions(dtoSpecs, generatedQueries)
            }
            .toMap()

        map.putAll(newDTOSpecs)
        // now we look for tables that have @Reference resolved from previous iterations
        next = queue.filter { (_, columnDeclarations) ->
            columnDeclarations
                .filterIsInstance<ColumnDeclaration.WithReference>()
                .map { tableLookupMap.getValue(it.reference.tableClassName.canonicalName) }
                .all { it in map }
        }
        // we remove the next from the queue
        queue = queue - next.keys

        // if there is nothing more to process next but the queue has still values
        // it means that there are cyclic references somewhere
        if (next.isEmpty() && queue.isNotEmpty()) logger.warn(buildString {
            appendLine("There is a cyclic @Reference in one of those tables: ")
            queue.keys.forEach {
                appendLine("\n\t - ${it.className.canonicalName}")
            }
        })
    }
    return map.values.toList()
}

fun generateDtos(
    tableDeclaration: TableDeclaration,
    dtoClassName: ClassName,
    updateQueryDtoClassName: ClassName,
    properties: List<DTOProperty>
): DTOSpecs {
    val dto = TypeSpec.classBuilder(dtoClassName)
        .addModifiers(KModifier.DATA)
        .addAnnotation(Serializable::class)
        .primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameters(properties.map { it.parameter })
                .build()
        )
        .addProperties(properties.map { it.property })
        .build()
    val typeVariable = TypeVariableName("T")
    val queryParamSpec = ParameterSpec("query", typeVariable)
    val queryPropertySpec = PropertySpec.builder("query", typeVariable)
        .initializer("query")
        .build()
    val updateParamSpec = ParameterSpec("update", dtoClassName)
    val updatePropertySpec = PropertySpec.builder("update", dtoClassName)
        .initializer("update")
        .build()
    val updateDto = TypeSpec.classBuilder(updateQueryDtoClassName)
        .addModifiers(KModifier.DATA)
        .addTypeVariable(typeVariable)
        .addAnnotation(Serializable::class)
        .primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter(queryParamSpec)
                .addParameter(updateParamSpec)
                .build()
        )
        .addProperty(queryPropertySpec)
        .addProperty(updatePropertySpec)
        .build()


    return DTOSpecs(
        tableDeclaration = tableDeclaration,
        dto = dto,
        updateQueryDto = updateDto,
        className = dtoClassName,
        updateQueryClassName = updateQueryDtoClassName,
        properties = properties
    )
}

