@file:Suppress("NAME_SHADOWING")

package com.github.lamba92.ktor.exposedrepositories.processor.endpoints

import com.github.lamba92.ktor.exposedrepositories.EndpointsSetup
import com.github.lamba92.ktor.exposedrepositories.RestRepositoriesConfiguration
import com.github.lamba92.ktor.exposedrepositories.processor.*
import com.github.lamba92.ktor.exposedrepositories.processor.capitalize
import com.github.lamba92.ktor.exposedrepositories.processor.decapitalize
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.Database

fun generateRouteFunctionSpec(
    dtoSpecs: DTOSpecs,
    functions: List<FunSpec>,
    clauseName: String
): FunSpec {
    val tableCName = dtoSpecs.tableDeclaration.className.simpleName.capitalize()
    return FunSpec.builder("install$tableCName${clauseName.capitalize()}Routes")
        .receiver(Route::class)
        .addParameter("database", Database::class)
        .addParameters(functions.flatMap { it.parameters.drop(1) }.toSet())
        .addCode(
            CodeBlock.builder()
                .foldOn(functions) { acc, funSpec ->
                    acc.addStatement("%N(", funSpec)
                        .indent()
                        .foldIndexedOn(funSpec.parameters) {index, acc, param ->
                            acc.addStatement(
                                "%N = %N".appendIf(index != funSpec.parameters.lastIndex, ","),
                                param, param
                            )
                        }
                        .unindent()
                        .addStatement(")")
                }
                .build()
        )
        .build()
}


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
    dtoSpecs: DTOSpecs,
    insertRouteInstallSpec: FunSpec,
    selectRouteInstallSpec: FunSpec,
    updateRouteInstallSpec: FunSpec,
    deleteRouteInstallSpec: FunSpec
): FunSpec {
    val tables = insertRouteInstallSpec.parameters.drop(1).dropLast(1)
    val optInCLassName = ClassName("kotlin", "OptIn")
    return FunSpec.builder("register${dtoSpecs.tableDeclaration.className.simpleName}")
        .addAnnotation(
            AnnotationSpec.builder(optInCLassName)
                .addMember("%T::class", ClassName("io.ktor.util", "InternalAPI"))
                .build()
        )
        .receiver(RestRepositoriesConfiguration::class)
        .addParameter("database", Database::class)
        .addParameter(
            ParameterSpec.builder("transactionIsolation", Int::class)
                .defaultValue("Connection.TRANSACTION_REPEATABLE_READ")
                .build()
        )
        .addParameters(tables)
        .addParameter(
            "configure",
            LambdaTypeName.get(EndpointsSetup::class.asTypeName().parameterizedBy(dtoSpecs.className), returnType = UNIT)
        )
        .addCode(
            CodeBlock.builder()
                .addStatement(
                    "val setup = EndpointsSetup<%T>(\"%L\")",
                    dtoSpecs.className,
                    dtoSpecs.tableDeclaration.names.plural.decapitalize()
                )
                .addStatement("\t.apply(configure)")
                .addStatement("val tablePath = setup.tablePath.filter { !it.isWhitespace() }")
                .addStatement(
                    "assert(tablePath.isNotEmpty()) { \"%T path cannot be blank or empty\" }",
                    dtoSpecs.tableDeclaration.className
                )
                .beginControlFlow("setup.configuredMethods.forEach { (endpoint, behaviour) ->")
                .beginControlFlow("addConfiguration(tablePath) {")
                .beginControlFlow("when(endpoint) {")
                .addStatement("Endpoint.INSERT -> %N(", insertRouteInstallSpec)
                .indent()
                .foldIndexedOn(insertRouteInstallSpec.parameters) { index, acc, next ->
                    acc.addStatement(
                        "%N = %N".appendIf(index != insertRouteInstallSpec.parameters.lastIndex, ","),
                        next, next
                    )
                }
                .unindent()
                .addStatement(")")
                .addStatement("Endpoint.SELECT -> %N(", selectRouteInstallSpec)
                .indent()
                .foldIndexedOn(selectRouteInstallSpec.parameters) { index, acc, next ->
                    acc.addStatement(
                        "%N = %N".appendIf(index != insertRouteInstallSpec.parameters.lastIndex, ","),
                        next, next
                    )
                }
                .unindent()
                .addStatement(")")
                .addStatement("Endpoint.UPDATE -> %N(", updateRouteInstallSpec)
                .indent()
                .foldIndexedOn(updateRouteInstallSpec.parameters) { index, acc, next ->
                    acc.addStatement(
                        "%N = %N".appendIf(index != updateRouteInstallSpec.parameters.lastIndex, ","),
                        next, next
                    )
                }
                .unindent()
                .addStatement(")")
                .addStatement("Endpoint.DELETE -> %N(", deleteRouteInstallSpec)
                .indent()
                .foldIndexedOn(deleteRouteInstallSpec.parameters) { index, acc, next ->
                    acc.addStatement(
                        "%N = %N".appendIf(index != deleteRouteInstallSpec.parameters.lastIndex, ","),
                        next, next
                    )
                }
                .unindent()
                .addStatement(")")
                .endControlFlow()
                .endControlFlow()
                .endControlFlow()
                .build()
        )
        .build()
}