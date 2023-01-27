@file:Suppress("NAME_SHADOWING")

package com.github.lamba92.ktor.exposedrepositories.processor.queries

import com.github.lamba92.ktor.exposedrepositories.processor.*
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import org.jetbrains.exposed.sql.Transaction

/*
params: queryParam, tableA. tableB
    val statement = tableA.select { tableA.id eq queryParam }.single()
    return DTO(
        id = statement[tableA.id]
        ciao = statement[tableA.ciao]
        referenced = selectReferencedByReferencedParam(statement[tableA.referencedParam], tableB)
    )
*/


context(LoggerContext)
fun generateSelectBySingleProperty(
    dtoSpecs: DTOSpecs,
    dtoProperty: DTOProperty,
    map: MutableMap<TableDeclaration, DTOSpecs.WithFunctions>
): FunSpec {
    val funSpec = FunSpec
        .builder("select${dtoSpecs.tableDeclaration.names.singular}By${dtoProperty.parameter.name.capitalize()}")
        .contextReceiver<Transaction>()
        .addParameter(dtoProperty.parameter.nonNullable())
        .addParameter(dtoSpecs.tableDeclaration.parameter)
        .returns(dtoSpecs.className)
    val tablesToAdd = mutableSetOf<ParameterSpec>()
    val codeBlock = CodeBlock.builder()
        .addStatement(
            format = "val statement = %N.select { %N.%L eq %N }.single()",
            dtoSpecs.tableDeclaration.parameter, dtoSpecs.tableDeclaration.parameter, dtoProperty.declarationSimpleName,
            dtoProperty.parameter.nonNullable()
        )
        .addStatement("return %T(", dtoSpecs.className)
        .indent()
        .foldIndexedOn(dtoSpecs.properties) { index, acc, next ->
            when (next) {
                is DTOProperty.Simple -> acc.addStatement(
                    "%N = statement[%N.%N]"
                        .appendIf(next.isEntityId, ".value")
                        .appendIf(index != dtoSpecs.properties.lastIndex, ","),
                    next.property, dtoSpecs.tableDeclaration.parameter, next.property
                )

                is DTOProperty.WithReference -> {
                    val selectSpec = map.getValue(next.reference.tableDeclaration)
                        .functions.selectBySingle.getValue(next.reference.propertyName)
                    val isNullable = next.declaration.type.resolve().arguments
                        .first().type!!.resolve().isMarkedNullable
                    val tables = selectSpec.parameters.drop(1)
                    tablesToAdd.addAll(tables)
                    if (!isNullable) {
                        acc.addStatement("%N = %N(", next.property, selectSpec)
                            .indent()
                            .addStatement(
                                format = "%L = statement[%N.%L],",
                                next.reference.propertyName, dtoSpecs.tableDeclaration.parameter,
                                next.declarationSimpleName
                            )
                            .foldIndexedOn(tables) { index, acc, tableParamSpec ->
                                acc.addStatement(
                                    "%N = %N".appendIf(index != tables.lastIndex, ","),
                                    tableParamSpec, tableParamSpec
                                )
                            }
                            .unindent()
                            .addStatement(")".appendIf(index != dtoSpecs.properties.lastIndex, ","))
                    } else {
                        acc.beginControlFlow(
                            "%N = statement[%N.%L]?.let {",
                            next.property, dtoSpecs.tableDeclaration.parameter, next.declarationSimpleName
                        )
                            .addStatement("%N(", selectSpec)
                            .indent()
                            .addStatement("%N = it,", selectSpec.parameters.first())
                            .foldOn(tables) { acc, tableParamSpec ->
                                acc.addStatement(
                                    "%N = %N".appendIf(index != tables.lastIndex, ","),
                                    tableParamSpec, tableParamSpec
                                )
                            }
                            .unindent()
                            .addStatement(")".appendIf(index != dtoSpecs.properties.lastIndex, ","))
                            .endControlFlow(if (index != dtoSpecs.properties.lastIndex) "," else "")
                    }
                }
            }

        }
        .unindent()
        .addStatement(")")
        .build()
    return funSpec
        .addParameters(tablesToAdd)
        .addCode(codeBlock)
        .build()
}

fun generateSelectByMultipleProperties(
    dtoSpecs: DTOSpecs,
    dtoProperty: DTOProperty,
    selectSingle: FunSpec
): FunSpec {
    val paramName = dtoProperty.parameter.name + "s"
    val tables = selectSingle.parameters.drop(1)
    val codeBlock = CodeBlock.builder()
        .beginControlFlow("return %L.map { ", paramName)
        .addStatement("%N(", selectSingle)
        .indent()
        .addStatement("%N = it,", selectSingle.parameters.first())
        .foldIndexedOn(tables) { index, acc, next ->
            acc.addStatement("%N = %N".appendIf(index != tables.lastIndex, ","), next, next)
        }
        .unindent()
        .addStatement(")")
        .endControlFlow()
        .build()
    val cPluralName = dtoSpecs.tableDeclaration.names.plural.capitalize()
    val cPropertyName = dtoProperty.declarationSimpleName.capitalize()
    return FunSpec.builder("select${cPluralName}By$cPropertyName")
        .contextReceiver<Transaction>()
        .addParameter(paramName, dtoProperty.parameter.type.copy(nullable = false).list())
        .addParameters(tables)
        .returns(dtoSpecs.className.list())
        .addCode(codeBlock)
        .build()
}