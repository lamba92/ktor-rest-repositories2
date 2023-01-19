package com.github.lamba92.ktor.restrepositories.processor.queries

import com.github.lamba92.ktor.restrepositories.processor.*
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
    val tableParameter = dtoSpecs.tableDeclaration.className.asParameter()
    val funSpec = FunSpec
        .builder("select${dtoSpecs.tableDeclaration.providedSingularName}By${dtoProperty.parameter.name.capitalize()}")
        .contextReceiver<Transaction>()
        .addParameter("parameter", dtoProperty.parameter.type.copy(nullable = false))
        .addParameter(tableParameter)
        .returns(dtoSpecs.dtoClassName)
    val tablesToAdd = mutableSetOf<ParameterSpec>()
    val codeBlock = CodeBlock.builder()
        .addStatement(
            format = "val statement = %N.select { %N.%L eq parameter }.single()",
            tableParameter, tableParameter, dtoProperty.declarationSimpleName
        )
        .addStatement("return %T(", dtoSpecs.dtoClassName)
        .indent()
        .foldIndexedOn(dtoSpecs.properties) { index, acc, next ->
            when (next) {
                is DTOProperty.Simple -> acc.addStatement(
                    "%N = statement[%N.%N]".appendIf(index != dtoSpecs.properties.lastIndex, ","),
                    next.property, tableParameter, next.property
                )

                is DTOProperty.WithReference -> {
                    val insertSpec = map.getValue(next.reference.tableDeclaration)
                        .functions.selectBySingle.getValue(next.reference.propertyName)
                    val isNullable = next.declaration.type.resolve().arguments
                        .first().type!!.resolve().isMarkedNullable
                    val tables = insertSpec.parameters.drop(1)
                    tablesToAdd.addAll(tables)
                    if (!isNullable) acc.addStatement("%N = %N(", next.property, insertSpec)
                        .indent()
                        .addStatement(
                            format = "parameter = statement[%N.%L],",
                            tableParameter,
                            next.declarationSimpleName
                        )
                        .foldOn(tables) { acc, tableParamSpec ->
                            acc.addStatement(
                                "%N = %N".appendIf(index != tables.lastIndex, ","),
                                tableParamSpec, tableParamSpec
                            )
                        }
                        .unindent()
                        .addStatement(")")
                    else acc.beginControlFlow(
                        "%N = statement[%N.%L]?.let {",
                        next.property, tableParameter, next.declarationSimpleName
                    )
                        .addStatement("%N(", insertSpec)
                        .indent()
                        .addStatement("parameter = it,")
                        .foldOn(tables) { acc, tableParamSpec ->
                            acc.addStatement(
                                "%N = %N".appendIf(index != tables.lastIndex, ","),
                                tableParamSpec, tableParamSpec
                            )
                        }
                        .unindent()
                        .addStatement(")")
                        .endControlFlow(if (index != dtoSpecs.properties.lastIndex) "," else "")
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
    val tables = selectSingle.parameters.drop(1)
    val codeBlock = CodeBlock.builder()
        .beginControlFlow("return parameters.map { ")
        .addStatement("%N(", selectSingle)
        .indent()
        .addStatement("parameter = it,")
        .foldIndexedOn(tables) { index, acc, next ->
            acc.addStatement("%N = %N".appendIf(index != tables.lastIndex, ","), next, next)
        }
        .unindent()
        .addStatement(")")
        .endControlFlow()
        .build()
    val cPluralName = dtoSpecs.tableDeclaration.providedPluralName.capitalize()
    val cPropertyName = dtoProperty.declarationSimpleName.capitalize()
    return FunSpec.builder("select${cPluralName}By$cPropertyName")
        .contextReceiver<Transaction>()
        .addParameter("parameters", dtoProperty.parameter.type.copy(nullable = false).list())
        .addParameters(tables)
        .returns(dtoSpecs.dtoClassName.list())
        .addCode(codeBlock)
        .build()
}