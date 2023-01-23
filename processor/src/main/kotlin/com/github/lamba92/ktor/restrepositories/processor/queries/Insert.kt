package com.github.lamba92.ktor.restrepositories.processor.queries

import com.github.lamba92.ktor.restrepositories.processor.*
import com.squareup.kotlinpoet.*
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
    val funSpec = FunSpec.builder("insert")
        .contextReceiver<Transaction>()
        .addParameter(dtoSpecs.parameter)
        .addParameter(dtoSpecs.tableDeclaration.parameter)
    val queue = mutableListOf<PropertyQueueElement>()
    val codeBlock = CodeBlock.builder()
        .beginControlFlow("val insertStatement = %N.insert {", dtoSpecs.tableDeclaration.parameter)
        .foldOn(dtoSpecs.properties) { acc, dtoProperty ->
            val cName = dtoProperty.parameter.name.capitalize()
            when (dtoProperty) {
                is DTOProperty.Simple -> acc.addStatement(
                    format = "%N.%N?.let { dto%L -> it[%N.%L] = dto%L }",
                    dtoSpecs.parameter, dtoProperty.property, cName, dtoSpecs.tableDeclaration.parameter,
                    dtoProperty.declarationSimpleName, cName
                )

                is DTOProperty.WithReference -> {
                    queue.add(
                        PropertyQueueElement(
                            dtoProperty = dtoProperty,
                            specWithFunctions = map.getValue(dtoProperty.reference.tableDeclaration)
                        )
                    )
                    acc.addStatement(
                        format = "%N.%N?.%N?.let { dto%L -> it[%N.%N] = dto%L }",
                        dtoSpecs.parameter,
                        dtoProperty.property, dtoProperty.reference.propertyName,
                        dtoProperty.declarationSimpleName.capitalize(),
                        dtoSpecs.tableDeclaration.parameter, dtoProperty.declarationSimpleName,
                        dtoProperty.declarationSimpleName.capitalize()
                    )
                }
            }
        }
        .endControlFlow()
    val tables = mutableSetOf<ParameterSpec>()
    queue.forEach { (dtoProperty, specsWithFunctions) ->
        codeBlock.beginControlFlow(
            "val %L = %N.%N?.let {",
            dtoProperty.reference.referencedJsonParameterName, dtoSpecs.parameter, dtoProperty.property
        )
        codeBlock.addStatement("%N(", specsWithFunctions.functions.insertSingle)
            .indent()
            .addStatement("%N = it,", dtoProperty.reference.dtoSpec.parameter)
        specsWithFunctions.functions.insertSingle
            .parameters
            .filter { it.type != dtoProperty.property.type.copy(nullable = false) }
            .forEachIndexed { index, insertParameter ->
                tables.add(insertParameter)
                codeBlock.addStatement(
                    "%N = %N".appendIf(index != specsWithFunctions.functions.insertSingle.parameters.size - 2, ","),
                    insertParameter, insertParameter
                )
            }
        codeBlock.unindent().addStatement(")")
        codeBlock.endControlFlow()
    }
    funSpec.addParameters(tables)
    codeBlock
        .addStatement("return %T(", dtoSpecs.className)
        .indent()
        .foldIndexedOn(dtoSpecs.properties) { index, acc, next ->
            when (next) {
                is DTOProperty.Simple -> acc.addStatement(
                    "%N = insertStatement[%N.%N]".appendIf(index != dtoSpecs.properties.lastIndex, ","),
                    next.property, dtoSpecs.tableDeclaration.parameter, next.property
                )

                is DTOProperty.WithReference -> acc.addStatement(
                    "%L = %N".appendIf(index != dtoSpecs.properties.lastIndex, ","),
                    next.reference.referencedJsonParameterName, next.property
                )
            }
        }
        .unindent()
        .addStatement(")")
    return funSpec
        .addCode(codeBlock.build())
        .returns(dtoSpecs.className)
        .build()
}

fun generateBulkInsert(dtoSpecs: DTOSpecs, singleInsertSpec: FunSpec): FunSpec {
    val returnType = TypeName.list(dtoSpecs.className)
    val queue = dtoSpecs.properties
        .filterIsInstance<DTOProperty.WithReference>()
        .toMutableList()
    val tables = mutableSetOf(dtoSpecs.tableDeclaration)
    while (queue.isNotEmpty()) {
        val next = queue.removeAt(0).reference
        tables.add(next.tableDeclaration)
        queue.addAll(next.dtoSpec.properties.filterIsInstance<DTOProperty.WithReference>())
    }
    val tableParams = tables.map { it.parameter }
    return FunSpec.builder("insert")
        .contextReceiver<Transaction>()
        .addParameter(ParameterSpec.builder("dtos", returnType).build())
        .addParameters(tableParams)
        .addCode(
            CodeBlock.builder()
                .add("return dtos.map { %N(it, ", singleInsertSpec)
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