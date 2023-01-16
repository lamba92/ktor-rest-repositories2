package com.github.lamba92.ktor.restrepositories.processor.endpoints

import com.github.lamba92.ktor.restrepositories.EndpointBehaviour
import com.github.lamba92.ktor.restrepositories.processor.DTOSpecs
import com.github.lamba92.ktor.restrepositories.processor.list
import com.github.lamba92.ktor.restrepositories.processor.capitalize
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.Database

fun generateDeleteRouteFunctionSpecForParam(
    dtoSpecs: DTOSpecs,
    tableTypeSpec: ClassName,
    parameterSpec: ParameterSpec,
    updateFunctionSpec: FunSpec,
): FunSpec {
    val nonNullableParameterSpecType = parameterSpec.type.copy(nullable = false)
    val getSingleContent: CodeBlockTransform = {
        it.addStatement("val param = call.receive<%T>()", nonNullableParameterSpecType)
            .beginControlFlow("newSuspendedTransaction(db = database) {")
            .addStatement(
                "val intercepted = behaviour.restRepositoryInterceptor(this, %T(%N = param))",
                dtoSpecs.dtoClassName,
                parameterSpec
            )
            .addStatement("val message = \"Parameter %T.%N\" +", tableTypeSpec, parameterSpec)
            .addStatement("\t%S", "is null after being transformed by the restRepositoryInterceptor")
            .addStatement("table.%N(requireNotNull(intercepted.%N) { message })", updateFunctionSpec, parameterSpec)
            .endControlFlow()
            .addStatement("call.respond(HttpStatusCode.OK)")
    }

    val getBulkContent: CodeBlockTransform = {
        it.addStatement("val params = call.receive<%T>()", TypeName.list(nonNullableParameterSpecType))
            .beginControlFlow("newSuspendedTransaction(db = database) {")
            .beginControlFlow("val intercepted = params.map { param ->")
            .addStatement("behaviour.restRepositoryInterceptor(this, %T(%N = param))", dtoSpecs.dtoClassName, parameterSpec)
            .endControlFlow()
            .beginControlFlow("intercepted.forEach { dto ->")
            .addStatement("val message = \"Parameter %T.%N\" +", tableTypeSpec, parameterSpec)
            .addStatement("\t%S", "is null after being transformed by the restRepositoryInterceptor")
            .addStatement("table.%N(requireNotNull(dto.%N) { message })", updateFunctionSpec, parameterSpec)
            .endControlFlow()
            .endControlFlow()
            .addStatement("call.respond(HttpStatusCode.OK)")
    }

    val getCheckAuthCode: CodeBlockTransform = {
        it.beginControlFlow("delete(\"by%L\") {", parameterSpec.name.capitalize())
            .let(getSingleContent)
            .endControlFlow()
            .beginControlFlow("delete(\"by%Ls\") {", parameterSpec.name.capitalize())
            .let(getBulkContent)
            .endControlFlow()
    }

    return FunSpec.builder("install" + tableTypeSpec.simpleName + "DeleteBy${parameterSpec.name.capitalize()}Route")
        .receiver(Route::class)
        .addParameter("table", tableTypeSpec)
        .addParameter("database", Database::class)
        .addParameter("behaviour", EndpointBehaviour::class.asTypeName().parameterizedBy(dtoSpecs.dtoClassName))
        .addCode(generateAuthCode(getCheckAuthCode, getCheckAuthCode))
        .build()
}