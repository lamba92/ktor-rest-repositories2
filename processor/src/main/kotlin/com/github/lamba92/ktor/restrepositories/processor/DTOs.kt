package com.github.lamba92.ktor.restrepositories.processor

import com.github.lamba92.ktor.restrepositories.processor.queries.generateBulkInsert
import com.github.lamba92.ktor.restrepositories.processor.queries.generateSingleInsert
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import kotlinx.serialization.Serializable

sealed interface DTOProperty {

    val originalColumnType: KSType
    val property: PropertySpec
    val parameter: ParameterSpec
    val declaration: KSPropertyDeclaration

    data class Simple(
        override val originalColumnType: KSType,
        override val property: PropertySpec,
        override val parameter: ParameterSpec,
        override val declaration: KSPropertyDeclaration
    ) : DTOProperty

    data class WithReference(
        override val originalColumnType: KSType,
        override val property: PropertySpec,
        override val parameter: ParameterSpec,
        override val declaration: KSPropertyDeclaration,
        val reference: Reference
    ) : DTOProperty {
        data class Reference(
            val dtoSpec: DTOSpecs,
            val propertyName: String,
            val tableDeclaration: TableDeclaration,
            val referencedJsonParameterName: String
        )
    }
}

data class TableDeclaration(
    val declaration: KSClassDeclaration,
    val providedSingularName: String,
    val providedPluralName: String,
    val className: ClassName = declaration.toClassName()
)

context(LoggerContext)
fun Sequence<TableDeclaration>.generateDTOSpecs(): Map<TableDeclaration, DTOSpecs.WithFunctions> {
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
                val dtoPropertiesSpecs = columnDeclarations.map {
                    when (it) {
                        is ColumnDeclaration.Simple -> it.generateDTOPropertiesSpecs()
                        is ColumnDeclaration.WithReference -> {
                            val referencedTableDeclaration = tableLookupMap
                                .getValue(it.referencedTableClassname.canonicalName)
                            it.generateDTOPropertiesSpecs(
                                referencedDtoSpec = map.getValue(referencedTableDeclaration).specs,
                                referencedTableDeclaration = referencedTableDeclaration
                            )
                        }
                    }
                }
                if (dtoPropertiesSpecs.isEmpty()) return@mapNotNull null
                val generatedPackageName = tableDeclaration.declaration.packageName.asString()
                val dtoClassName = ClassName(
                    packageName = "$generatedPackageName.dto",
                    tableDeclaration.providedSingularName
                )
                val updateQueryDtoClassName = ClassName(
                    packageName = "$generatedPackageName.dto",
                    "${tableDeclaration.providedSingularName}UpdateQuery"
                )
                val generateDtos = generateDtos(tableDeclaration, dtoClassName, updateQueryDtoClassName, dtoPropertiesSpecs)
                val generatedQueries = GeneratedQueryFunctions(
                    generateSingleInsert(generateDtos, map),
                    generateBulkInsert(generateDtos, map)
                )
                tableDeclaration to DTOSpecs.WithFunctions(generateDtos, generatedQueries)
            }
            .toMap()

        map.putAll(newDTOSpecs)
        // now we look for tables that have @Reference resolved from previous iterations
        next = queue.filter { (_, columnDeclarations) ->
            columnDeclarations
                .filterIsInstance<ColumnDeclaration.WithReference>()
                .map { tableLookupMap.getValue(it.referencedTableClassname.canonicalName) }
                .all { it in map }
        }
        // we remove the next from the queue
        queue = queue - next.keys

        // if there is nothing more to process next but the queue has still values
        // it means that there are cyclic references somewhere
        if (next.isEmpty() && queue.isNotEmpty()) error(buildString {
            appendLine("There is a cyclic @Reference in one of those tables: ")
            queue.keys.forEach {
                appendLine("\n\t - ${it.className.canonicalName}")
            }
        })
    }
    return map.toMap()
}

private fun <K, V> Map<K, V>.partition(partition: (K, V) -> Boolean) =
    entries.map { Pair(it.key, it.value) }
        .partition { (k, v) -> partition(k, v) }
        .let { it.first.toMap() to it.second.toMap() }

fun generateDtos(tableDeclaration: TableDeclaration,
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
        dtoClassName = dtoClassName,
        updateQueryDtoClassName = updateQueryDtoClassName,
        properties = properties.associateBy { it.declaration.simpleName.asString() })
}

data class DTOSpecs(
    val tableDeclaration: TableDeclaration,
    val dto: TypeSpec,
    val updateQueryDto: TypeSpec,
    val dtoClassName: ClassName,
    val updateQueryDtoClassName: ClassName,
    val properties: Map<String, DTOProperty>
) {
    data class WithFunctions(val specs: DTOSpecs, val functions: GeneratedQueryFunctions)
}