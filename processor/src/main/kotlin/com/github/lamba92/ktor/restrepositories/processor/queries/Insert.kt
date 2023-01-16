package com.github.lamba92.ktor.restrepositories.processor.queries

import com.github.lamba92.ktor.restrepositories.processor.*
import com.squareup.kotlinpoet.*
import org.jetbrains.exposed.sql.Transaction

fun generateSingleInsert(dtoClassName: ClassName, allProperties: List<ParameterSpec>, tableTypeSpec: ClassName) =
    FunSpec.builder("insert")
        .contextReceivers(Transaction::class.asTypeName())
        .receiver(tableTypeSpec)
        .addParameter(ParameterSpec.builder("dto", dtoClassName).build())
        .addCode(
            CodeBlock.builder()
                .beginControlFlow("val insertStatement = insert {")
                .foldOn(allProperties) { acc, next ->
                    val cName = next.name.capitalize()
                    acc.addStatement("dto.%N?.let { dto%L -> it[%N] = dto%L }", next, cName, next, cName)
                }
                .endControlFlow()
                .addStatement("return %T(", dtoClassName)
                .foldIndexedOn(allProperties) { index, acc, next ->
                    acc.addStatement(
                        format = "\t%N = insertStatement[%N]".appendIf(allProperties.lastIndex != index, ","),
                        next, next
                    )
                }
                .addStatement(")")
                .build()
        )
        .returns(dtoClassName)
        .build()

fun generateBulkInsert(
    dtoClassName: ClassName,
    allProperties: List<ParameterSpec>,
    tableTypeSpec: ClassName
): FunSpec {
    val returnType = TypeName.list(dtoClassName)
    return FunSpec.builder("insert")
        .contextReceivers(Transaction::class.asTypeName())
        .receiver(tableTypeSpec)
        .addParameter(ParameterSpec.builder("dtos", returnType).build())
        .addCode(
            CodeBlock.builder()
                .beginControlFlow("return dtos.map { dto ->")
                .beginControlFlow("val insertStatement = insert {")
                .foldOn(allProperties) { acc, next ->
                    val cName = next.name.capitalize()
                    acc.addStatement("dto.%N?.let { dto%L -> it[%N] = dto%L }", next, cName, next, cName)
                }
                .endControlFlow()
                .addStatement("%T(", dtoClassName)
                .foldIndexedOn(allProperties) { index, acc, next ->
                    acc.addStatement(
                        format = "\t%N = insertStatement[%N]".appendIf(index != allProperties.lastIndex, ","),
                        next, next
                    )
                }
                .addStatement(")")
                .endControlFlow()
                .build()
        )
        .returns(returnType)
        .build()
}