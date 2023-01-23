package com.github.lamba92.ktor.restrepositories.processor.endpoints

import com.github.lamba92.ktor.restrepositories.EndpointBehaviour
import com.github.lamba92.ktor.restrepositories.processor.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.Database

fun generateSelectRouteFunctionSpecForParam(dtoProperty: DTOProperty, dtoSpec: DTOSpecs.WithFunctions): FunSpec {
    val nonNullableParamSpecType = dtoProperty.parameter.type.copy(nullable = false)
    val selectSingle = dtoSpec.functions.selectBySingle
        .getValue(dtoProperty.declarationSimpleName)
    val selectBulk = dtoSpec.functions.selectByMultiple
        .getValue(dtoProperty.declarationSimpleName)
    val tableCName = dtoSpec.specs.tableDeclaration.names.singular.decapitalize()

    fun getSingleContent(): CodeBlockTransform {
        return {
            it.addStatement("val %N = call.receive<%T>()", dtoProperty.parameter, nonNullableParamSpecType)
                .beginControlFlow("val result = newSuspendedTransaction(db = database)")
                .addStatement("val %L = %N(", tableCName, selectSingle)
                .indent()
                .foldIndexedOn(selectSingle.parameters) { index,  acc, next ->
                    acc.addStatement(
                        "%N = %N".appendIf(index != selectSingle.parameters.lastIndex, ","),
                        next, next
                    )
                }
                .unindent()
                .addStatement(")")
                .addStatement("behaviour.restRepositoryInterceptor(this, %L)", tableCName)
                .endControlFlow()
                .addStatement("call.respond(result)")
        }
    }

    fun getBulkContent(): CodeBlockTransform = {
        it.addStatement("val %Ns = call.receive<%T>()", dtoProperty.parameter, TypeName.list(nonNullableParamSpecType))
            .beginControlFlow("val results = newSuspendedTransaction(db = database) {")
            .addStatement("%N(", selectBulk)
            .indent()
            .foldIndexedOn(selectBulk.parameters) { index, acc, next ->
                acc.addStatement(
                    "%N = %N".appendIf(index != selectBulk.parameters.lastIndex, ","),
                    next, next
                )
            }
            .unindent()
            .addStatement(")")
            .addStatement("\t.map { behaviour.restRepositoryInterceptor(this, it) }")
            .endControlFlow()
            .addStatement("call.respond(results)")
    }

    val cParamName = dtoProperty.parameter.name.capitalize()
    val getCheckAuthCode: CodeBlockTransform = {
        it.beginControlFlow("get(\"by%L\")", cParamName)
            .let(getSingleContent())
            .endControlFlow()
            .beginControlFlow("get(\"by%Ls\")", cParamName)
            .let(getBulkContent())
            .endControlFlow()
    }
    val tableSimpleName = dtoSpec.specs.tableDeclaration.className.simpleName
    return FunSpec.builder("install${tableSimpleName}SelectBy${cParamName}Routes")
        .receiver(Route::class)
        .addParameter("database", Database::class)
        .addParameters(selectSingle.parameters.drop(1))
        .addParameter("behaviour", EndpointBehaviour::class.asTypeName().parameterizedBy(dtoSpec.specs.className))
        .addCode(generateAuthCode(getCheckAuthCode, getCheckAuthCode))
        .build()
}