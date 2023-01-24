package com.github.lamba92.ktor.exposedrepositories.processor.queries

import com.github.lamba92.ktor.exposedrepositories.processor.DTOProperty
import com.github.lamba92.ktor.exposedrepositories.processor.DTOSpecs
import com.github.lamba92.ktor.exposedrepositories.processor.capitalize
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import org.jetbrains.exposed.sql.Transaction

fun generateDeleteBySingleProperty(
    property: DTOProperty,
    dtoSpecs: DTOSpecs,
): FunSpec {
    return FunSpec
        .builder("delete${dtoSpecs.tableDeclaration.names.plural}By${property.declarationSimpleName.capitalize()}")
        .contextReceiver<Transaction>()
        .addParameter(property.declarationSimpleName, property.parameter.type.copy(nullable = false))
        .addParameter(dtoSpecs.tableDeclaration.parameter)
        .addCode(CodeBlock.of(
            "%N.deleteWhere·{·%N.%L·eq·%L·}",
            dtoSpecs.tableDeclaration.parameter, dtoSpecs.tableDeclaration.parameter,
            property.declarationSimpleName, property.declarationSimpleName
        ))
        .build()
}

