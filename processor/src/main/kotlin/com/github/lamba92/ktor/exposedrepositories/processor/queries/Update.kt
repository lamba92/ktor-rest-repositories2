@file:Suppress("NAME_SHADOWING")

package com.github.lamba92.ktor.exposedrepositories.processor.queries

import com.github.lamba92.ktor.exposedrepositories.processor.*
import com.squareup.kotlinpoet.*
import org.jetbrains.exposed.sql.Transaction

/*
params: dto, param1, tableA, tableB

update ({ tableA.param1 eq param1 }) { statement ->
    statement[tableA.param1] = dto.param1 // simple param
    statement[tableA.tableBId] = dto.tableB.id
}

updateTableBById(dto.tableB, dto.tableB.id, tableB)
 */


fun generateUpdateBySingleProperty(
    dtoSpecs: DTOSpecs,
    dtoProperty: DTOProperty,
    map: MutableMap<TableDeclaration, DTOSpecs.WithFunctions>,
): FunSpec {
    val mainTablePropertySpec = dtoSpecs.tableDeclaration.parameter
    val providedSingularName = dtoSpecs.tableDeclaration.names.singular
    val cDtoName = dtoProperty.declarationSimpleName.capitalize()
    val tables = mutableListOf<PropertyQueueElement>()

    val propertiesWithReference = dtoSpecs.properties
        .filterIsInstance<DTOProperty.WithReference>()
        .filterNot { it.originalColumnType.isMarkedNullable }

    val codeBlock = CodeBlock.builder()
        .foldOn(propertiesWithReference) { acc, next ->
            acc.addStatement(
                "val %LDto = requireNotNull(%N.%L) { \"%L.%L is null\" }",
                next.reference.referencedJsonParameterName, dtoSpecs.parameter,
                next.reference.referencedJsonParameterName, dtoSpecs.className.simpleName,
                next.reference.referencedJsonParameterName
            )
                .addStatement(
                    "val %LDto%L = requireNotNull(%LDto.%L) { \"%L.%L.%L is null\" }",
                    next.reference.referencedJsonParameterName, next.reference.propertyName.capitalize(),
                    next.reference.referencedJsonParameterName, next.reference.propertyName,
                    dtoSpecs.className.simpleName, next.reference.referencedJsonParameterName,
                    next.reference.propertyName
                )
        }
        .beginControlFlow(
            controlFlow = "%N.update({·%N.%L·eq·%N·})·{·statement·->", mainTablePropertySpec,
            mainTablePropertySpec, dtoProperty.declarationSimpleName, dtoProperty.parameter
        )
        .foldOn(dtoSpecs.properties) { acc, next ->
            when (next) {
                is DTOProperty.Simple -> {
                    if (next.originalColumnType.isMarkedNullable) {
                        acc.addStatement(
                            "statement[%N.%N] = %N.%N",
                            mainTablePropertySpec, next.declarationSimpleName,
                            mainTablePropertySpec, next.declarationSimpleName
                        )
                    } else {
                        acc.addStatement(
                            "statement[%N.%N] =",
                            mainTablePropertySpec, next.declarationSimpleName,
                        )
                            .addStatement(
                                "\trequireNotNull(%N.%N) { \"%L.%N is null\" }",
                                mainTablePropertySpec, next.declarationSimpleName,
                                dtoSpecs.className.simpleName, next.declarationSimpleName
                            )
                    }
                }

                is DTOProperty.WithReference -> {
                    tables.add(PropertyQueueElement(next, map.getValue(next.reference.tableDeclaration)))
                    if (next.originalColumnType.isMarkedNullable) {
                        acc.addStatement(
                            "statement[%N.%L] = %N.%L?.%L",
                            mainTablePropertySpec, next.declarationSimpleName,
                            dtoSpecs.parameter, next.reference.referencedJsonParameterName,
                            next.reference.propertyName
                        )
                    } else {
                        acc.addStatement(
                            "statement[%N.%L] = %LDto%L",
                            mainTablePropertySpec, next.declarationSimpleName,
                            next.reference.referencedJsonParameterName, next.reference.propertyName.capitalize()
                        )
                    }
                }
            }
        }
        .endControlFlow()
        .foldOn(tables) { acc, (referencedProperty, referencedDtoSpecsWithFunctions) ->
            val updateSpecByParam = referencedDtoSpecsWithFunctions.functions
                .update.getValue(referencedProperty.reference.propertyName)
            val params = updateSpecByParam.parameters.drop(2)
            if (referencedProperty.originalColumnType.isMarkedNullable) {
                acc.beginControlFlow(
                    "%N.%L?.let {",
                    dtoSpecs.parameter, referencedProperty.reference.referencedJsonParameterName
                )
                    .addStatement("%N(", updateSpecByParam)
                    .indent()
                    .addStatement("%N = it,", updateSpecByParam.parameters.first())
                    .addStatement("%L = requireNotNull(it.%L) { \"%L.%L.%L\" },",
                        referencedProperty.reference.propertyName, referencedProperty.reference.propertyName,
                        dtoSpecs.className.simpleName, referencedProperty.reference.referencedJsonParameterName,
                        referencedProperty.reference.propertyName
                    )
                    .foldIndexedOn(params) { index, acc, next ->
                        acc.addStatement("%N = %N".appendIf(index != params.lastIndex, ","), next, next)
                    }
                    .unindent()
                    .addStatement(")")
                    .endControlFlow()
            } else {
                acc.addStatement("%N(", updateSpecByParam)
                    .indent()
                    .addStatement(
                        "%N = %LDto,",
                        updateSpecByParam.parameters.first(),
                        referencedProperty.reference.referencedJsonParameterName,
                    )
                    .addStatement(
                        "%L = %NDto%N,",
                        referencedProperty.reference.propertyName,
                        referencedProperty.reference.referencedJsonParameterName,
                        referencedProperty.reference.propertyName.capitalize(),
                    )
                    .foldIndexedOn(params) { index, acc, next ->
                        acc.addStatement("%N = %N".appendIf(index != params.lastIndex, ","), next, next)
                    }
                    .unindent()
                    .addStatement(")")
            }
        }
        .build()
    return FunSpec
        .builder("update${providedSingularName}By$cDtoName")
        .contextReceiver<Transaction>()
        .addParameter(dtoSpecs.parameter)
        .addParameter(dtoProperty.parameter.nonNullable())
        .addParameter(mainTablePropertySpec)
        .addParameters(tables.map { it.specWithFunctions.specs.tableDeclaration.parameter })
        .addCode(codeBlock)
        .build()
}

fun ParameterSpec.nonNullable() = ParameterSpec(name, type.copy(nullable = false), modifiers)
