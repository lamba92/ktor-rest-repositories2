package com.github.lamba92.ktor.restrepositories.processor.queries

import com.github.lamba92.ktor.restrepositories.processor.*
import com.squareup.kotlinpoet.*
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction

/*
tableA.insert {
    dto.a?.let { aNew -> it[a] = aNew }
    dto.b?.let { bNew -> it[b] = bNew.id }
}
dto.b?.let { bNew -> insert(b, tableB, tableC) }
 */
data class PropertyQueueElement(
    val dtoProperty: DTOProperty.WithReference,
    val specWithFunctions: DTOSpecs.WithFunctions
)

fun generateSingleInsert(
    dtoSpecs: DTOSpecs,
    map: MutableMap<TableDeclaration, DTOSpecs.WithFunctions>
): FunSpec {
    val mainTableParamName = dtoSpecs.tableDeclaration.className.simpleName.decapitalize()
    val funSpec = FunSpec.builder("insert")
        .contextReceivers(Transaction::class.asTypeName())
        .addParameter(ParameterSpec.builder("dto", dtoSpecs.dtoClassName).build())
        .addParameter(mainTableParamName, dtoSpecs.tableDeclaration.className)
    val queue = mutableListOf<PropertyQueueElement>()
    val codeBlock = CodeBlock.builder()
        .beginControlFlow("val insertStatement = %L.insert {", mainTableParamName)
        .foldOn(dtoSpecs.properties) { acc, dtoProperty ->
            val cName = dtoProperty.parameter.name.capitalize()
            when (dtoProperty) {
                is DTOProperty.Simple -> acc.addStatement(
                    format = "dto.%N?.let { dto%L -> it[%L.%L] = dto%L }",
                    dtoProperty.property, cName, mainTableParamName, dtoProperty.declarationSimpleName, cName
                )
                is DTOProperty.WithReference -> {
                    queue.add(
                        PropertyQueueElement(
                            dtoProperty = dtoProperty,
                            specWithFunctions = map.getValue(dtoProperty.reference.tableDeclaration)
                        )
                    )
                    acc.addStatement(
                        format = "dto.%N?.%N?.let { dto%L -> it[%L.%N] = dto%L }",
                        dtoProperty.property, dtoProperty.reference.propertyName, dtoProperty.declarationSimpleName.capitalize(),
                        mainTableParamName, dtoProperty.declarationSimpleName, dtoProperty.declarationSimpleName.capitalize()
                    )
                }
            }
        }
        .endControlFlow()
    val tables = mutableSetOf<ParameterSpec>()
    queue.forEach { (dtoProperty, specsWithFunctions) ->
        codeBlock.beginControlFlow(
            "val %L = dto.%N?.let {",
            dtoProperty.reference.referencedJsonParameterName, dtoProperty.property
        )
        codeBlock.addStatement("%N(", specsWithFunctions.functions.insertSingle)
        codeBlock.addStatement("\tdto = it,")
        specsWithFunctions.functions.insertSingle
            .parameters
            .filter { it.type != dtoProperty.property.type.copy(nullable = false) }
            .forEachIndexed { index, insertParameter ->
                tables.add(insertParameter)
                codeBlock.addStatement(
                    "\t%N = %N".appendIf(index != specsWithFunctions.functions.insertSingle.parameters.size - 2, ","),
                    insertParameter, insertParameter
                )
            }
        codeBlock.addStatement(")")
        codeBlock.endControlFlow()
    }
    funSpec.addParameters(tables)
    codeBlock
        .addStatement("return %T(", dtoSpecs.dtoClassName)
        .foldIndexedOn(dtoSpecs.properties) { index, acc, next ->
            when (next) {
                is DTOProperty.Simple -> acc.addStatement(
                    "\t%N = insertStatement[%L.%N]".appendIf(index != dtoSpecs.properties.lastIndex, ","),
                    next.property, mainTableParamName, next.property
                )
                is DTOProperty.WithReference -> acc.addStatement(
                    "\t%L = %N".appendIf(index != dtoSpecs.properties.lastIndex, ","),
                    next.reference.referencedJsonParameterName, next.property
                )
            }
        }
        .addStatement(")")
    return funSpec
        .addCode(codeBlock.build())
        .returns(dtoSpecs.dtoClassName)
        .build()
}

fun generateBulkInsert(dtoSpecs: DTOSpecs, singleInsertSpec: FunSpec): FunSpec {
    val returnType = TypeName.list(dtoSpecs.dtoClassName)
    val queue = dtoSpecs.properties
        .filterIsInstance<DTOProperty.WithReference>()
        .toMutableList()
    val tables = mutableListOf(dtoSpecs.tableDeclaration)
    while (queue.isNotEmpty()) {
        val next = queue.removeAt(0).reference
        tables.add(next.tableDeclaration)
        queue.addAll(next.dtoSpec.properties.filterIsInstance<DTOProperty.WithReference>())
    }
    val tableParams = tables.map {
        ParameterSpec.builder(
            name = it.className.simpleName.decapitalize(),
            type = it.className
        )
            .build()
    }
    return FunSpec.builder("insert")
        .contextReceiver<Transaction>()
        .receiver(dtoSpecs.tableDeclaration.className)
        .addParameter(ParameterSpec.builder("dtos", returnType).build())
        .foldOn(tableParams) { acc, next ->
            acc.addParameter(next)
        }
        .addCode(
            CodeBlock.builder()
                .addStatement("return dtos.map { %N(it, ", singleInsertSpec)
                .foldIndexedOn(tableParams) { index, acc, next ->
                    acc.add("%N".appendIf(index != tableParams.lastIndex, ", "), next)
                }
                .add(") }")
                .build()
        )
        .returns(returnType)
        .build()
}

inline fun <reified T> FunSpec.Builder.contextReceiver() =
    contextReceivers(T::class.asTypeName())