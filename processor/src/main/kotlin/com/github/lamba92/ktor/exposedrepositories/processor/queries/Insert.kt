package com.github.lamba92.ktor.exposedrepositories.processor.queries

import com.github.lamba92.ktor.exposedrepositories.processor.*
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

context(LoggerContext)
fun generateSingleInsert(
    dtoSpecs: DTOSpecs,
    map: MutableMap<TableDeclaration, DTOSpecs.WithFunctions>
): FunSpec {
    val funSpec = FunSpec.builder("insert${dtoSpecs.tableDeclaration.names.singular.capitalize()}")
        .contextReceiver<Transaction>()
        .addParameter(dtoSpecs.parameter)
        .addParameter(dtoSpecs.tableDeclaration.parameter)
    val propertiesWithReference =
        dtoSpecs.properties.filterIsInstance<DTOProperty.WithReference>()
    val tables = mutableSetOf<ParameterSpec>()

    val codeBlock = CodeBlock.builder()
        .foldOn(propertiesWithReference) { acc, dtoProperty ->
            val specsWithFunctions = map.getValue(dtoProperty.reference.tableDeclaration)
            val parameters = specsWithFunctions.functions.insertSingle.parameters
            val insertTablesParams = parameters.drop(1)
            tables.addAll(insertTablesParams)
            if (!dtoProperty.property.type.isNullable) {
                acc.addStatement(
                    "val %L = %N(",
                    dtoProperty.reference.referencedJsonParameterName, specsWithFunctions.functions.insertSingle
                )
                    .indent()
                    .addStatement(
                        "%N = %N.%N,",
                        parameters.first(), dtoSpecs.parameter, dtoProperty.property
                    )
                    .foldIndexedOn(insertTablesParams) { index, acc, next ->
                        acc.addStatement(
                            "%N = %N".appendIf(index != parameters.lastIndex, ","),
                            next, next
                        )
                    }
                    .unindent()
                    .addStatement(")")
            } else {
                acc.beginControlFlow(
                    "val %L = %N.%N?.let {",
                    dtoProperty.reference.referencedJsonParameterName, dtoSpecs.parameter, dtoProperty.property
                )
                    .addStatement("%N(", specsWithFunctions.functions.insertSingle)
                    .indent()
                    .addStatement("%N = it,", dtoProperty.reference.dtoSpec.parameter)
                    .foldIndexedOn(insertTablesParams) { index, acc, parameter ->
                        acc.addStatement(
                            "%N = %N".appendIf(index != insertTablesParams.lastIndex, ","),
                            parameter, parameter
                        )
                    }
                    .unindent()
                    .addStatement(")")
                    .endControlFlow()
            }
        }
        .beginControlFlow("val insertStatement = %N.insert { statement ->", dtoSpecs.tableDeclaration.parameter)
        .foldOn(dtoSpecs.properties) { acc, dtoProperty ->
            val cName = dtoProperty.parameter.name.capitalize()
            when (dtoProperty) {
                is DTOProperty.Simple -> acc.addStatement(
                    "statement[%N.%L] = %N.%N",
                    dtoSpecs.tableDeclaration.parameter, dtoProperty.declarationSimpleName,
                    dtoSpecs.parameter, dtoProperty.property
                )

                is DTOProperty.WithReference -> {
                    val isReferenceNullable =
                        dtoProperty.reference.dtoSpec.properties
                            .first { it.property.name == dtoProperty.reference.propertyName }
                            .property.type.isNullable
                    if (dtoProperty.property.type.isNullable || isReferenceNullable) {
                        val initial = buildString {
                            append("%N")
                            if (dtoProperty.property.type.isNullable) append("?")
                            append(".%N")
                            if (dtoProperty.property.type.isNullable || isReferenceNullable) append("?")
                        }
                        acc.addStatement(
                            format = "$initial.let { statement[%N.%N] = it }",
                            dtoProperty.property, dtoProperty.reference.propertyName,
                            dtoSpecs.tableDeclaration.parameter, dtoProperty.declarationSimpleName
                        )
                    } else acc.addStatement(
                        "statement[%N.%N] = %N.%N",
                        dtoSpecs.tableDeclaration.parameter, dtoProperty.declarationSimpleName,
                        dtoProperty.property, dtoProperty.reference.propertyName
                    )

                }
            }
        }
        .endControlFlow()
    funSpec.addParameters(tables)
    codeBlock
        .addStatement("return %T(", dtoSpecs.className)
        .indent()
        .foldIndexedOn(dtoSpecs.properties) { index, acc, next ->
            when (next) {
                is DTOProperty.Simple -> acc.addStatement(
                    "%N = insertStatement[%N.%N]"
                        .appendIf(next.isEntityId, ".value")
                        .appendIf(index != dtoSpecs.properties.lastIndex, ","),
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

context(LoggerContext)
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
    val cName = dtoSpecs.tableDeclaration.names.plural.decapitalize()
    return FunSpec.builder("insert")
        .contextReceiver<Transaction>()
        .addParameter(ParameterSpec.builder(cName, returnType).build())
        .addParameters(tableParams)
        .addCode(
            CodeBlock.builder()
                .add("return %L.map { %N(it, ", cName, singleInsertSpec)
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