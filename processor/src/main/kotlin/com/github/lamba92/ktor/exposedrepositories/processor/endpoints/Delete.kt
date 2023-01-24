package com.github.lamba92.ktor.exposedrepositories.processor.endpoints

import com.github.lamba92.ktor.exposedrepositories.EndpointBehaviour
import com.github.lamba92.ktor.exposedrepositories.processor.*
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.asTypeName
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.Database

fun generateDeleteRouteFunctionSpecForParam(
    dtoSpecs: DTOSpecs.WithFunctions,
    dtoProperty: DTOProperty
): FunSpec {
    val nonNullableParameterSpecType = dtoProperty.parameter.type.copy(nullable = false)
    val deleteSpec = dtoSpecs.functions.delete.getValue(dtoProperty.declarationSimpleName)
    val deleteParameters = deleteSpec.parameters.drop(1)
    val getSingleContent: CodeBlockTransform = {
        it.addStatement("val param = call.receive<%T>()", nonNullableParameterSpecType)
            .beginControlFlow("newSuspendedTransaction(db = database) {")
            .addStatement(
                "val intercepted = behaviour.restRepositoryInterceptor(this, %T(%N = param))",
                dtoSpecs.specs.className,
                dtoProperty.parameter
            )
            .addStatement(
                "val message = \"Parameter %T.%N\" +",
                dtoSpecs.specs.tableDeclaration.className, dtoProperty.property
            )
            .addStatement("\t%S", "is null after being transformed by the restRepositoryInterceptor")
            .addStatement("%N(", deleteSpec)
            .indent()
            .addStatement(
                "%N = requireNotNull(intercepted.%N) { message },",
                deleteSpec.parameters.first(), dtoProperty.parameter
            )
            .foldIndexedOn(deleteParameters) { index, acc, next ->
                acc.addStatement(
                    "%N = %N".appendIf(index != deleteParameters.lastIndex, ","),
                    next, next
                )
            }
            .unindent()
            .addStatement(")")
            .endControlFlow()
            .addStatement("call.respond(HttpStatusCode.OK)")
    }

    val getBulkContent: CodeBlockTransform = {
        it.addStatement("val params = call.receive<%T>()", nonNullableParameterSpecType.list())
            .beginControlFlow("newSuspendedTransaction(db = database) {")
            .beginControlFlow("val intercepted = params.map { param ->")
            .addStatement(
                "behaviour.restRepositoryInterceptor(this, %T(%N = param))",
                dtoSpecs.specs.className, dtoProperty.parameter
            )
            .endControlFlow()
            .beginControlFlow("intercepted.forEach { dto ->")
            .addStatement(
                "val message = \"Parameter %T.%N\" +",
                dtoSpecs.specs.tableDeclaration.className, dtoProperty.property
            )
            .addStatement("\t%S", "is null after being transformed by the restRepositoryInterceptor")
            .addStatement("%N(", deleteSpec)
            .indent()
            .addStatement(
                "%N = requireNotNull(dto.%N) { message },",
                deleteSpec.parameters.first(), dtoProperty.parameter
            )
            .foldIndexedOn(deleteParameters) { index, acc, next ->
                acc.addStatement(
                    "%N = %N".appendIf(index != deleteParameters.lastIndex, ","),
                    next, next
                )
            }
            .unindent()
            .addStatement(")")
            .endControlFlow()
            .endControlFlow()
            .addStatement("call.respond(HttpStatusCode.OK)")
    }

    val paramCName = dtoProperty.parameter.name.capitalize()
    val getCheckAuthCode: CodeBlockTransform = {
        it.beginControlFlow("delete(\"by%L\") {", paramCName)
            .let(getSingleContent)
            .endControlFlow()
            .beginControlFlow("delete(\"by%Ls\") {", paramCName)
            .let(getBulkContent)
            .endControlFlow()
    }

    val tableName = dtoSpecs.specs.tableDeclaration.className.simpleName
    return FunSpec.builder("install${tableName}DeleteBy${paramCName}Route")
        .receiver(Route::class)
        .addParameter("database", Database::class)
        .addParameters(deleteParameters)
        .addParameter("behaviour", EndpointBehaviour::class.asTypeName().parameterizedBy(dtoSpecs.specs.className))
        .addCode(generateAuthCode(getCheckAuthCode, getCheckAuthCode))
        .build()
}