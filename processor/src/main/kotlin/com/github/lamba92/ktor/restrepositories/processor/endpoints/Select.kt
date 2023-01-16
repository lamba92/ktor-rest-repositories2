package com.github.lamba92.ktor.restrepositories.processor.endpoints

import com.github.lamba92.ktor.restrepositories.EndpointBehaviour
import com.github.lamba92.ktor.restrepositories.processor.list
import com.github.lamba92.ktor.restrepositories.processor.capitalize
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.Database

fun generateSelectRouteFunctionSpecForParam(
    dtoClassName: ClassName,
    tableTypeSpec: ClassName,
    parameterSpec: ParameterSpec,
    selectSingle: FunSpec,
    selectBulk: FunSpec
): FunSpec {
    val nonNullableParamSpecType = parameterSpec.type.copy(nullable = false)
    fun getSingleContent(): CodeBlockTransform = {
        it.addStatement("val param = call.receive<%T>()", nonNullableParamSpecType)
            .beginControlFlow("val result = newSuspendedTransaction(db = database)")
            .addStatement("table.%N(param).map { behaviour.restRepositoryInterceptor(this, it) }", selectSingle)
            .endControlFlow()
            .addStatement("call.respond(result)")
    }

    fun getBulkContent(): CodeBlockTransform = {
        it.addStatement("val params = call.receive<%T>()", TypeName.list(nonNullableParamSpecType))
            .beginControlFlow("val result = newSuspendedTransaction(db = database)")
            .addStatement("table.%N(params).map { behaviour.restRepositoryInterceptor(this, it) }", selectBulk)
            .endControlFlow()
            .addStatement("call.respond(result)")
    }

    val getCheckAuthCode: CodeBlockTransform = {
        it.beginControlFlow("get(\"by%L\")", parameterSpec.name.capitalize())
            .let(getSingleContent())
            .endControlFlow()
            .beginControlFlow("get(\"by%Ls\")", parameterSpec.name.capitalize())
            .let(getBulkContent())
            .endControlFlow()
    }
    return FunSpec.builder("install" + tableTypeSpec.simpleName + "SelectBy${parameterSpec.name.capitalize()}Routes")
        .receiver(Route::class)
        .addParameter("table", tableTypeSpec)
        .addParameter("database", Database::class)
        .addParameter("behaviour", EndpointBehaviour::class.asTypeName().parameterizedBy(dtoClassName))
        .addCode(generateAuthCode(getCheckAuthCode, getCheckAuthCode))
        .build()
}