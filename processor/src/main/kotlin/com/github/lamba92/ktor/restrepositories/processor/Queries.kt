package com.github.lamba92.ktor.restrepositories.processor

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.asTypeName
import org.jetbrains.exposed.sql.Transaction

fun generateSingleInsert(dtoClassName: ClassName, allProperties: List<ParameterSpec>, tableTypeSpec: ClassName) =
    FunSpec.builder("insert")
        .contextReceivers(Transaction::class.asTypeName())
        .receiver(tableTypeSpec)
        .addParameter(ParameterSpec.builder("dto", dtoClassName).build())
        .addCode(buildString {
            appendLine("val insertStatement = insert {")
            allProperties.forEach {
                val name = it.name
                appendLine("\tdto.$name?.let { dto${name.capitalize()} -> it[$name] = dto${name.capitalize()} }")
            }
            appendLine("}")
            appendLine("return ${dtoClassName.simpleName}(")
            allProperties.forEachIndexed { index, property ->
                val name = property.name
                append("\tinsertStatement[$name]")
                if (index != allProperties.lastIndex) appendLine(",")
                else appendLine()
            }
            appendLine(")")
        })
        .returns(dtoClassName)
        .build()

fun generateBulkSingleInsert(
    dtoClassName: ClassName,
    allProperties: List<ParameterSpec>,
    tableTypeSpec: ClassName
): FunSpec {
    val returnType = ClassName("kotlin.collections", "List")
        .parameterizedBy(dtoClassName)
    return FunSpec.builder("insert")
        .contextReceivers(Transaction::class.asTypeName())
        .receiver(tableTypeSpec)
        .addParameter(
            ParameterSpec.builder(
                "dtos",
                returnType
            ).build()
        )
        .addCode(buildString {
            appendLine("return dtos.map { dto ->")
            appendLine("\tval insertStatement = insert {")
            allProperties.forEach {
                val name = it.name
                appendLine("\t\tdto.$name?.let { dto${name.capitalize()} -> it[$name] = dto${name.capitalize()} }")
            }
            appendLine("\t}")
            appendLine("\t${dtoClassName.simpleName}(")
            allProperties.forEachIndexed { index, property ->
                val name = property.name
                append("\t\tinsertStatement[$name]")
                if (index != allProperties.lastIndex) appendLine(",")
                else appendLine()
            }
            appendLine("\t)")
            appendLine("}")
        })
        .returns(returnType)
        .build()
}

fun generateUpdateBySingleProperty(
    dtoClassName: ClassName,
    parameter: ParameterSpec,
    allParameters: List<ParameterSpec>,
    tableTypeSpec: ClassName,
) = FunSpec
    .builder("update${tableTypeSpec.simpleName}By${parameter.name.capitalize()}")
    .contextReceivers(Transaction::class.asTypeName())
    .receiver(tableTypeSpec)
    .addParameter("parameter", parameter.type.copy(nullable = false))
    .addParameter("dto", dtoClassName)
    .returns(Int::class)
    .addCode(buildString {
        appendLine("// Query for updating by ${parameter.name}")
        appendLine("return update({ ${parameter.name} eq parameter }) {")
        allParameters.forEach { param ->
            appendLine(
                "\tdto.${param.name}?.let { new${param.name.capitalize()} " +
                        "-> it[${param.name}] = new${param.name.capitalize()} }"
            )
        }
        appendLine("}")
    })
    .build()

fun generateSelectBySingleProperty(
    dtoClassName: ClassName,
    parameter: ParameterSpec,
    allParameters: List<ParameterSpec>,
    tableTypeSpec: ClassName,
) = FunSpec
    .builder("get${tableTypeSpec.simpleName.appendIfMissing("s")}By${parameter.name.capitalize()}")
    .contextReceivers(Transaction::class.asTypeName())
    .receiver(tableTypeSpec)
    .addParameter("parameter", parameter.type.copy(nullable = false))
    .returns(ClassName("kotlin.collections", "List").parameterizedBy(dtoClassName))
    .addCode(buildString {
        appendLine("// Query for ${parameter.name}")
        appendLine("return select { ${parameter.name} eq parameter }")
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

fun generateDeleteBySingleProperty(
    parameter: ParameterSpec,
    tableTypeSpec: ClassName,
) = FunSpec
    .builder("delete${tableTypeSpec.simpleName.appendIfMissing("s")}By${parameter.name.capitalize()}")
    .contextReceivers(Transaction::class.asTypeName())
    .receiver(tableTypeSpec)
    .addParameter("parameter", parameter.type.copy(nullable = false))
    .returns(Int::class)
    .addCode(buildString {
        appendLine("// Query for deleting ${parameter.name}")
        appendLine("return deleteWhere { ${parameter.name} eq parameter }")
    })
    .build()

fun generateSelectByMultipleProperties(
    dtoClassName: ClassName,
    parameter: ParameterSpec,
    allParameters: List<ParameterSpec>,
    tableTypeSpec: ClassName
) = FunSpec
    .builder("get${tableTypeSpec.simpleName.appendIfMissing("s")}By${parameter.name.capitalize().appendIfMissing("s")}")
    .contextReceivers(Transaction::class.asTypeName())
    .receiver(tableTypeSpec)
    .addParameter(
        "parameters",
        ClassName("kotlin.collections", "List")
            .parameterizedBy(parameter.type.copy(nullable = false))
    )
    .returns(ClassName("kotlin.collections", "List").parameterizedBy(dtoClassName))
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

private fun String.appendIfMissing(ending: String) =
    if (endsWith(ending)) this else this + ending