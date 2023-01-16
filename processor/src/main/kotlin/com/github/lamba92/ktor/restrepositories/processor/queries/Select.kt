package com.github.lamba92.ktor.restrepositories.processor.queries

import com.github.lamba92.ktor.restrepositories.processor.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.jetbrains.exposed.sql.Transaction

fun generateSelectBySingleProperty(
    dtoClassName: ClassName,
    parameter: ParameterSpec,
    allParameters: List<ParameterSpec>,
    tableTypeSpec: ClassName,
) = FunSpec.builder("select${tableTypeSpec.simpleName.appendIfMissing("s")}By${parameter.name.capitalize()}")
    .contextReceivers(Transaction::class.asTypeName())
    .receiver(tableTypeSpec)
    .addParameter("parameter", parameter.type.copy(nullable = false))
    .returns(TypeName.list(dtoClassName))
    .addCode(
        CodeBlock.builder()
            .addStatement("val query = select { ${parameter.name} eq parameter }")
            .beginControlFlow("return query.map { statement ->")
            .addStatement("%T(", dtoClassName)
            .foldIndexedOn(allParameters) { index, acc, next ->
                acc.addStatement(
                    "\t%N = statement[%N]".appendIf(index != allParameters.lastIndex, ","),
                    next, next
                )
            }
            .addStatement(")")
            .endControlFlow()
            .build()
    )
    .build()

fun generateSelectByMultipleProperties(
    dtoClassName: ClassName,
    parameter: ParameterSpec,
    allParameters: List<ParameterSpec>,
    tableTypeSpec: ClassName
) = FunSpec.builder(
    "select${tableTypeSpec.simpleName.appendIfMissing("s")}By${
        parameter.name.capitalize().appendIfMissing("s")
    }"
)
    .contextReceivers(Transaction::class.asTypeName())
    .receiver(tableTypeSpec)
    .addParameter(
        "parameters",
        ClassName("kotlin.collections", "List")
            .parameterizedBy(parameter.type.copy(nullable = false))
    )
    .returns(TypeName.list(dtoClassName))
    .addCode(buildString {
        appendLine("// Query for a list of ${parameter.name.appendIfMissing("s")}")
        appendLine("return select { ${parameter.name} inList parameters }")
        appendLine("\t.map {")
        appendLine("\t\t${dtoClassName.simpleName}(")
        allParameters.forEachIndexed { index, param ->
            append("\t\t\tit[${param.name}]")
            if (index != allParameters.lastIndex) appendLine(",")
            else appendLine()
        }
        appendLine("\t\t)")
        appendLine("}")
    })
    .build()