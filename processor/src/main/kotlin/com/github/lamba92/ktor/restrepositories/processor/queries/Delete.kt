package com.github.lamba92.ktor.restrepositories.processor.queries

import com.github.lamba92.ktor.restrepositories.processor.appendIfMissing
import com.github.lamba92.ktor.restrepositories.processor.capitalize
import com.squareup.kotlinpoet.*
import org.jetbrains.exposed.sql.Transaction

fun generateDeleteBySingleProperty(
    parameter: ParameterSpec,
    tableTypeSpec: ClassName,
) = FunSpec
    .builder("delete${tableTypeSpec.simpleName.appendIfMissing("s")}By${parameter.name.capitalize()}")
    .contextReceivers(Transaction::class.asTypeName())
    .receiver(tableTypeSpec)
    .addParameter("parameter", parameter.type.copy(nullable = false))
    .returns(Int::class)
    .addCode(CodeBlock.of("return deleteWhere{·${parameter.name}·eq·parameter·}"))
    .build()

