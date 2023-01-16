package com.github.lamba92.ktor.restrepositories.processor.endpoints

import com.github.lamba92.ktor.restrepositories.EndpointBehaviour
import com.github.lamba92.ktor.restrepositories.EndpointsSetup
import com.github.lamba92.ktor.restrepositories.RestRepositoriesConfiguration
import com.github.lamba92.ktor.restrepositories.processor.capitalize
import com.github.lamba92.ktor.restrepositories.processor.decapitalize
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.Database


fun generateRouteFunctionSpec(
    dtoClassName: ClassName,
    tableTypeSpec: ClassName,
    functions: List<FunSpec>,
    clauseName: String
) = FunSpec.builder("install${tableTypeSpec.simpleName.capitalize()}${clauseName.capitalize()}Routes")
    .receiver(Route::class)
    .addParameter("table", tableTypeSpec)
    .addParameter("database", Database::class)
    .addParameter("behaviour", EndpointBehaviour::class.asTypeName().parameterizedBy(dtoClassName))
    .addCode(buildString {
        functions.forEach { appendLine("${it.name}(table, database, behaviour)") }
    })
    .build()

typealias CodeBlockTransform = (CodeBlock.Builder) -> CodeBlock.Builder

fun generateAuthCode(authContent: CodeBlockTransform, unAuthContent: CodeBlockTransform) =
    CodeBlock.builder()
        .beginControlFlow("if (behaviour.isAuthenticated) {")
        .beginControlFlow("authenticate(*behaviour.authNames.toTypedArray(), strategy = behaviour.authStrategy) {")
        .let(authContent)
        .endControlFlow()
        .nextControlFlow("else")
        .let(unAuthContent)
        .endControlFlow()
        .build()

fun generateTableEndpointSetup(
    dtoClassName: ClassName,
    tableTypeSpec: ClassName,
    insertRouteInstallSpec: FunSpec,
    selectRouteInstallSpec: FunSpec,
    updateRouteInstallSpec: FunSpec,
    deleteRouteInstallSpec: FunSpec
) =
    FunSpec.builder("registerTable")
        .receiver(RestRepositoriesConfiguration::class)
        .addParameter(ParameterSpec.builder("table", tableTypeSpec).build())
        .addParameter("database", Database::class)
        .addParameter(
            ParameterSpec.builder("transactionIsolation", Int::class)
                .defaultValue("Connection.TRANSACTION_REPEATABLE_READ")
                .build()
        )
        .addParameter(
            "configure",
            LambdaTypeName.get(EndpointsSetup::class.asTypeName().parameterizedBy(dtoClassName), returnType = UNIT)
        )
        .addCode(
            CodeBlock.builder()
                .addStatement(
                    "val setup = EndpointsSetup<%T>(\"%L\")",
                    dtoClassName,
                    tableTypeSpec.simpleName.decapitalize()
                )
                .addStatement("\t.apply(configure)")
                .addStatement("val tablePath = setup.tablePath.filter { !it.isWhitespace() }")
                .addStatement("assert(tablePath.isNotEmpty()) { \"%T path cannot be blank or empty\" }", tableTypeSpec)
                .beginControlFlow("setup.configuredMethods.forEach { (endpoint, behaviour) ->")
                .beginControlFlow("addConfiguration(tablePath) {")
                .beginControlFlow("when(endpoint) {")
                .addStatement("Endpoint.INSERT -> %N(table, database, behaviour)", insertRouteInstallSpec)
                .addStatement("Endpoint.SELECT -> %N(table, database, behaviour)", selectRouteInstallSpec)
                .addStatement("Endpoint.UPDATE -> %N(table, database, behaviour)", updateRouteInstallSpec)
                .addStatement("Endpoint.DELETE -> %N(table, database, behaviour)", deleteRouteInstallSpec)
                .endControlFlow()
                .endControlFlow()
                .endControlFlow()
                .build()
        )
        .build()