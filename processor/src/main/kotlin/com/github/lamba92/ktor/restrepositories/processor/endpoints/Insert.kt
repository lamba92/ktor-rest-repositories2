package com.github.lamba92.ktor.restrepositories.processor.endpoints

import com.github.lamba92.ktor.restrepositories.EndpointBehaviour
import com.github.lamba92.ktor.restrepositories.processor.*
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.asTypeName
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.Database

fun generateInsertRouteFunctionSpec(dtoSpec: DTOSpecs.WithFunctions): FunSpec {
    val tables = mutableListOf<ParameterSpec>()
    val dtoPluralName = dtoSpec.specs.tableDeclaration.names.plural.decapitalize()
    val dtoSingularName = dtoSpec.specs.tableDeclaration.names.singular.decapitalize()
    fun getSingleContent(): CodeBlockTransform {
        return {
            val insertSpec = dtoSpec.functions.insertSingle
            val firstParam = insertSpec.parameters.first()
            tables.addAll(insertSpec.parameters.drop(1))
            it.addStatement("val %L = call.receive<%T>()", dtoSingularName, dtoSpec.specs.className)
                .beginControlFlow("val result = newSuspendedTransaction(db = database) {")
                .addStatement("%N(", insertSpec)
                .indent()
                .addStatement("%N = behaviour.restRepositoryInterceptor(this, %L),", firstParam ,dtoSingularName)
                .foldIndexedOn(tables) { index, acc, next ->
                    acc.addStatement("%N = %N".appendIf(index != tables.lastIndex, ","), next, next)
                }
                .unindent()
                .addStatement(")")
                .endControlFlow()
                .addStatement("call.respond(result)")
        }
    }

    fun getBulkContent(): CodeBlockTransform = {
        val insertSpec = dtoSpec.functions.insertBulk
        val firstParam = dtoSpec.functions.insertBulk.parameters.first()
        it.addStatement("val %L = call.receive<%T>()", dtoPluralName, dtoSpec.specs.className.list())
            .beginControlFlow("val result = newSuspendedTransaction(db = database) {")
            .beginControlFlow("val result = %L.map {", dtoPluralName)
            .addStatement("%N(", insertSpec)
            .indent()
            .addStatement("%N = behaviour.restRepositoryInterceptor(this, it)", firstParam ,dtoSingularName)
            .foldIndexedOn(tables) { index, acc, next ->
                acc.addStatement("%N = %N".appendIf(index != tables.lastIndex, ","), next, next)
            }
            .unindent()
            .endControlFlow()
            .endControlFlow()
            .addStatement("call.respond(result)")
    }

    val getCheckAuthCode: CodeBlockTransform = {
        it.beginControlFlow("put {")
            .let(getSingleContent())
            .endControlFlow()
            .beginControlFlow("put(\"bulk\") {")
            .let(getBulkContent())
            .endControlFlow()
    }
    return FunSpec.builder("install" + dtoSpec.specs.tableDeclaration.className.simpleName + "InsertRoutes")
        .receiver(Route::class)
        .addParameters(tables)
        .addParameter("database", Database::class.asTypeName())
        .addParameter("behaviour", EndpointBehaviour::class.asTypeName().parameterizedBy(dtoSpec.specs.className))
        .addCode(generateAuthCode(getCheckAuthCode, getCheckAuthCode))
        .build()
}