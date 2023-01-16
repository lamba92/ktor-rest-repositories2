package com.github.lamba92.ktor.restrepositories.processor.endpoints

import com.github.lamba92.ktor.restrepositories.EndpointBehaviour
import com.github.lamba92.ktor.restrepositories.processor.list
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.Database

fun generateInsertRouteFunctionSpec(
    dtoSpec: ClassName,
    tableTypeSpec: ClassName,
    insertSingle: FunSpec,
    insertBulk: FunSpec
): FunSpec {
    fun getSingleContent(): CodeBlockTransform = {
        it.addStatement("val dto = call.receive<%T>()", dtoSpec)
            .beginControlFlow("val result = newSuspendedTransaction(db = database) {")
            .addStatement("table.%N(behaviour.restRepositoryInterceptor(this, dto))",  insertSingle)
            .endControlFlow()
            .addStatement("call.respond(result)")
    }

    fun getBulkContent(): CodeBlockTransform = {
        it.addStatement("val dtos = call.receive<%T>()", TypeName.list(dtoSpec))
            .beginControlFlow("val result = newSuspendedTransaction(db = database) {")
            .addStatement("table.%N(dtos.map { behaviour.restRepositoryInterceptor(this, it) })", insertBulk)
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
    return FunSpec.builder("install" + tableTypeSpec.simpleName + "InsertRoutes")
        .receiver(Route::class)
        .addParameter("table", tableTypeSpec)
        .addParameter("database", Database::class)
        .addParameter("behaviour", EndpointBehaviour::class.asTypeName().parameterizedBy(dtoSpec))
        .addCode(generateAuthCode(getCheckAuthCode, getCheckAuthCode))
        .build()
}