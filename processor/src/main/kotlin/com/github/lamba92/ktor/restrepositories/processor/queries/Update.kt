package com.github.lamba92.ktor.restrepositories.processor.queries

import com.github.lamba92.ktor.restrepositories.processor.DTOProperty
import com.github.lamba92.ktor.restrepositories.processor.capitalize
import com.github.lamba92.ktor.restrepositories.processor.foldOn
import com.squareup.kotlinpoet.*
import org.jetbrains.exposed.sql.Transaction

fun generateUpdateBySingleProperty(
    dtoClassName: ClassName,
    dtoPropertiesSpecs: List<DTOProperty>,
    tableTypeSpec: ClassName,
    dtoParameter: ParameterSpec,
) = FunSpec.builder("update${tableTypeSpec.simpleName}By${dtoParameter.name.capitalize()}")
    .contextReceivers(Transaction::class.asTypeName())
    .receiver(tableTypeSpec)
    .addParameter("parameter", dtoParameter.type.copy(nullable = false))
    .addParameter("dto", dtoClassName)
    .returns(Int::class)
    .addCode(
        CodeBlock.builder()
            .beginControlFlow("return update({·${dtoParameter.name}·eq·parameter·})·{·statement·->")
            .foldOn(dtoPropertiesSpecs) { acc, next ->
                val initial = acc.add("\t\tstatement[%N] = ", next.parameter)
                if (next.originalColumnType.isMarkedNullable) initial.addStatement("dto.%N", next.parameter)
                else initial.addStatement(
                    "requireNotNull(dto.%N) { \"%T.$%N is null\" }",
                    next.parameter, dtoClassName, next.parameter
                )
            }
            .endControlFlow()

            .build()
    )
    .build()