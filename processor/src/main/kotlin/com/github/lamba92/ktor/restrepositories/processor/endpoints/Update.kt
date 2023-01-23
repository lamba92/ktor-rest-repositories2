package com.github.lamba92.ktor.restrepositories.processor.endpoints

import com.github.lamba92.ktor.restrepositories.EndpointBehaviour
import com.github.lamba92.ktor.restrepositories.processor.*
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.asTypeName
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.Database

fun generateUpdateRouteFunctionSpecForParam(
    dtoSpecs: DTOSpecs.WithFunctions,
    dtoProperty: DTOProperty
): FunSpec {
    val nonNullableParameterSpecType = dtoProperty.parameter.type.copy(nullable = false)
    val updateFunctionSpec = dtoSpecs.functions
        .update
        .getValue(dtoProperty.declarationSimpleName)
    val queryParameter = updateFunctionSpec.parameters[1]
    val otherParameters = updateFunctionSpec.parameters - queryParameter
    val getSingleContent: CodeBlockTransform = {
        it.addStatement(
            "val updateQuery = call.receive<%T>()",
            dtoSpecs.specs.updateQueryClassName.parameterizedBy(nonNullableParameterSpecType)
        )
            .beginControlFlow("newSuspendedTransaction(db = database) {")
            .addStatement(
                "val %N = behaviour.restRepositoryInterceptor(this, updateQuery.update)",
                updateFunctionSpec.parameters.first()
            )
            .addStatement("%N(", updateFunctionSpec)
            .indent()
            .addStatement("%N = updateQuery.query,", updateFunctionSpec.parameters[1])
            .foldIndexedOn(otherParameters) { index, acc, next ->
                acc.addStatement(
                    "%N = %N".appendIf(index != updateFunctionSpec.parameters.lastIndex, ","),
                    next, next
                )
            }
            .unindent()
            .addStatement(")")
            .endControlFlow()
            .addStatement("call.respond(HttpStatusCode.OK)")
    }

    val propertyCName = dtoProperty.declarationSimpleName.capitalize()
    val getCheckAuthCode: CodeBlockTransform = {
        it.beginControlFlow("post(\"by%L\") {", propertyCName)
            .let(getSingleContent)
            .endControlFlow()
    }
    val tableSimpleName = dtoSpecs.specs.tableDeclaration.className.simpleName.capitalize()
    return FunSpec.builder("install" + tableSimpleName + "UpdateBy${propertyCName}Routes")
        .receiver(Route::class)
        .addParameter("database", Database::class)
        .addParameters(updateFunctionSpec.parameters.drop(2))
        .addParameter("behaviour", EndpointBehaviour::class.asTypeName().parameterizedBy(dtoSpecs.specs.className))
        .addCode(generateAuthCode(getCheckAuthCode, getCheckAuthCode))
        .build()
}