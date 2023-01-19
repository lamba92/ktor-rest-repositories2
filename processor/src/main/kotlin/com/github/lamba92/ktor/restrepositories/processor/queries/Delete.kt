package com.github.lamba92.ktor.restrepositories.processor.queries

import com.github.lamba92.ktor.restrepositories.processor.DTOSpecs
import com.github.lamba92.ktor.restrepositories.processor.appendIfMissing
import com.github.lamba92.ktor.restrepositories.processor.capitalize
import com.squareup.kotlinpoet.*
import org.jetbrains.exposed.sql.Transaction

fun generateDeleteBySingleProperty(
    parameter: ParameterSpec,
    dtoSpecs: DTOSpecs,
) = FunSpec
    .builder("delete${dtoSpecs.tableDeclaration.providedPluralName}By${parameter.name.capitalize()}")
    .contextReceiver<Transaction>()
    .addParameter("parameter", parameter.type.copy(nullable = false))
    .returns(Int::class)
    .addCode(CodeBlock.of("return deleteWhere{·${parameter.name}·eq·parameter·}"))
    .build()

