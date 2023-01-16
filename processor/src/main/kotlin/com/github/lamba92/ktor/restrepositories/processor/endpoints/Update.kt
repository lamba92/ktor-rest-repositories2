package com.github.lamba92.ktor.restrepositories.processor.endpoints

import com.github.lamba92.ktor.restrepositories.EndpointBehaviour
import com.github.lamba92.ktor.restrepositories.processor.DTOSpecs
import com.github.lamba92.ktor.restrepositories.processor.capitalize
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.asTypeName
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.Database

fun generateUpdateRouteFunctionSpecForParam(
    dtoSpecs: DTOSpecs,
    tableTypeSpec: ClassName,
    parameterSpec: ParameterSpec,
    updateFunctionSpec: FunSpec,
): FunSpec {
    val nonNullableParameterSpecType = parameterSpec.type.copy(nullable = false)
    val getSingleContent: CodeBlockTransform = {
        it.addStatement("val updateDto = call.receive<%T>()", dtoSpecs.updateQueryDtoClassName.parameterizedBy(nonNullableParameterSpecType))
            .beginControlFlow("newSuspendedTransaction(db = database) {")
            .addStatement("val intercepted = behaviour.restRepositoryInterceptor(this, updateDto.update)")
            .addStatement("table.%N(updateDto.query, intercepted)", updateFunctionSpec)
            .endControlFlow()
            .addStatement("call.respond(updateDto)")
    }

    val getCheckAuthCode: CodeBlockTransform = {
        it.beginControlFlow("post(\"by%L\") {", parameterSpec.name.capitalize())
            .let(getSingleContent)
            .endControlFlow()
    }
    return FunSpec.builder("install" + tableTypeSpec.simpleName + "UpdateBy${parameterSpec.name.capitalize()}Routes")
        .receiver(Route::class)
        .addParameter("table", tableTypeSpec)
        .addParameter("database", Database::class)
        .addParameter("behaviour", EndpointBehaviour::class.asTypeName().parameterizedBy(dtoSpecs.dtoClassName))
        .addCode(generateAuthCode(getCheckAuthCode, getCheckAuthCode))
        .build()
}