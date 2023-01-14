package com.github.lamba92.ktor.restrepositories.processor

import com.github.lamba92.ktor.restrepositories.RestRepositoriesConfiguration
import com.github.lamba92.ktor.restrepositories.RestRepositoriesConfiguration.EndpointsSetup
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.Database
import java.util.*


data class GeneratedFunctions(
    val insert: FunSpec,
    val selectBySingle: Map<ParameterSpec, FunSpec>,
    val selectByMultiple: Map<ParameterSpec, FunSpec>,
    val delete: Map<ParameterSpec, FunSpec>,
    val update: Map<ParameterSpec, FunSpec>
)

fun StringBuilder.appendLine(tabs: Int, value: String) =
    (0 until tabs).fold(this) { acc, count -> acc.append("\t") }
        .appendLine(value)
fun generateInsertRouteFunctionSpec(
    dtoSpec: ClassName,
    tableTypeSpec: ClassName,
    insertSpec: FunSpec
): FunSpec {
    fun getContent(tabs: Int) = buildString {
        appendLine()
        appendLine(tabs, "val dto = call.receive<${dtoSpec.simpleName}>()")
        appendLine(tabs, "val result = newSuspendedTransaction(db = database) {")
        appendLine(tabs, "\tbehaviour.restRepositoryInterceptor(this, table.insert(dto))")
        appendLine(tabs, "}")
        appendLine(tabs, "call.respond(result)")
    }

    fun getCheckAuthCode(tabs: Int) = buildString {
        appendLine(tabs, "get(path) {")
        appendLine(tabs, getContent(tabs + 1))
        appendLine(tabs, "}")
    }
    return FunSpec.builder("install" + dtoSpec.simpleName + "InsertRoutes")
        .receiver(Route::class)
        .addParameter("table", tableTypeSpec)
        .addParameter("database", Database::class)
        .addParameter("path", String::class)
        .addParameter("behaviour", EndpointsSetup.Behaviour::class.asTypeName().parameterizedBy(dtoSpec))
        .addCode(buildString {
            appendLine("if (behaviour.isAuthenticated) {")
            appendLine("\tauthenticate(*behaviour.authNames.toTypedArray(), strategy = behaviour.authStrategy) {")
            appendLine(getCheckAuthCode(2))
            appendLine("\t}")
            appendLine("} else {")
            appendLine(getCheckAuthCode(1))
            appendLine("}")

        })
        .build()
}

private fun String.decapitalize() = replaceFirstChar { it.lowercase(Locale.getDefault()) }

fun generateTableEndpointSetup(
    dtoSpec: ClassName,
    tableTypeSpec: ClassName,
    generatedFunctions: GeneratedFunctions,
    allParameters: List<ParameterSpec>
) =
    FunSpec.builder("registerTable")
        .receiver(RestRepositoriesConfiguration::class)
        .addParameter(ParameterSpec.builder("table", tableTypeSpec).build())
        .addParameter("database", Database::class)
        .addParameter(
            ParameterSpec.builder("transactionIsolation", Int::class)
                .defaultValue("java.sql.Connection.TRANSACTION_REPEATABLE_READ")
                .build()
        )
        .addParameter(
            "configure",
            LambdaTypeName.get(EndpointsSetup::class.asTypeName().parameterizedBy(dtoSpec), returnType = UNIT)
        )
        .addCode(buildString {
            appendLine("val setup = EndpointsSetup<${dtoSpec.simpleName}>(\"${tableTypeSpec.simpleName.lowercase()}\")")
            appendLine("\t.apply(configure)")
            appendLine("val tablePath = setup.tablePath.filter { !it.isWhitespace() }.isNotBlank()")
            appendLine("assert(tablePath) { \"${tableTypeSpec.simpleName} path cannot be blank or empty\" }")

            appendLine("setup.configuredMethods.forEach { (httpMethod, behaviour) ->")
            appendLine("\tentitiesConfigurationMap[RestRepositoriesRouteSetupKey(tablePath, httpMethod)] = {")
            appendLine("\t\twhen(httpMethod) {")
            appendLine("\t\t\tHttpMethod.GET -> ")
            appendLine("\t\t\t}")
            appendLine("\t\t}")
            appendLine("\t}")
            appendLine("}")
        })