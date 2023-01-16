package com.github.lamba92.ktor.restrepositories.processor

import com.github.lamba92.ktor.restrepositories.EndpointBehaviour
import com.github.lamba92.ktor.restrepositories.EndpointsSetup
import com.github.lamba92.ktor.restrepositories.RestRepositoriesConfiguration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.Database
import java.util.*


data class GeneratedFunctions(
    val insertSingle: FunSpec,
    val insertBulk: FunSpec,
    val selectBySingle: Map<ParameterSpec, FunSpec>,
    val selectByMultiple: Map<ParameterSpec, FunSpec>,
    val delete: Map<ParameterSpec, FunSpec>,
    val update: Map<ParameterSpec, FunSpec>
)

fun StringBuilder.appendLine(tabs: Int, value: String) =
    (0 until tabs).fold(this) { acc, _ -> acc.append("\t") }
        .appendLine(value)

fun generateInsertRouteFunctionSpec(
    dtoSpec: ClassName,
    tableTypeSpec: ClassName,
    insertSingle: FunSpec,
    insertBulk: FunSpec
): FunSpec {
    fun getSingleContent(tabs: Int) = buildString {
        appendLine()
        appendLine(tabs, "val dto = call.receive<${dtoSpec.simpleName}>()")
        appendLine(tabs, "val result = newSuspendedTransaction(db = database) {")
        appendLine(tabs, "\ttable.${insertSingle.name}(behaviour.restRepositoryInterceptor(this, dto))")
        appendLine(tabs, "}")
        appendLine(tabs, "call.respond(result)")
    }

    fun getBulkContent(tabs: Int) = buildString {
        appendLine()
        appendLine(tabs, "val dtos = call.receive<List<${dtoSpec.simpleName}>>()")
        appendLine(tabs, "val result = newSuspendedTransaction(db = database) {")
        appendLine(tabs, "\ttable.${insertBulk.name}(dtos.map { behaviour.restRepositoryInterceptor(this, it) })")
        appendLine(tabs, "}")
        appendLine(tabs, "call.respond(result)")
    }

    fun getCheckAuthCode(tabs: Int) = buildString {
        appendLine(tabs, "put(path) {")
        appendLine(tabs, getSingleContent(tabs + 1))
        appendLine(tabs, "}")
        appendLine(tabs, "put(\"\$path/bulk\") {")
        appendLine(tabs, getBulkContent(tabs + 1))
        appendLine(tabs, "}")
    }
    return FunSpec.builder("install" + tableTypeSpec.simpleName + "InsertRoutes")
        .receiver(Route::class)
        .addParameter("table", tableTypeSpec)
        .addParameter("database", Database::class)
        .addParameter("path", String::class)
        .addParameter("behaviour", EndpointBehaviour::class.asTypeName().parameterizedBy(dtoSpec))
        .addCode(generateAuthCode(getCheckAuthCode(2), getCheckAuthCode(1)))
        .build()
}

fun generateSelectRouteFunctionSpecForParam(
    dtoClassName: ClassName,
    tableTypeSpec: ClassName,
    parameterSpec: ParameterSpec,
    selectSingle: FunSpec,
    selectBulk: FunSpec
): FunSpec {
    fun getSingleContent(tabs: Int) = buildString {
        appendLine()
        appendLine(tabs, "val param = call.receive<${parameterSpec.type.copy(nullable = false)}>()")
        appendLine(tabs, "val result = newSuspendedTransaction(db = database) {")
        appendLine(tabs, "\ttable.${selectSingle.name}(param).map { behaviour.restRepositoryInterceptor(this, it) }")
        appendLine(tabs, "}")
        appendLine(tabs, "call.respond(result)")
    }

    fun getBulkContent(tabs: Int) = buildString {
        appendLine()
        appendLine(tabs, "val params = call.receive<List<${parameterSpec.type.copy(nullable = false)}>>()")
        appendLine(tabs, "val result = newSuspendedTransaction(db = database) {")
        appendLine(tabs, "\ttable.${selectBulk.name}(params).map { behaviour.restRepositoryInterceptor(this, it) }")
        appendLine(tabs, "}")
        appendLine(tabs, "call.respond(result)")
    }

    fun getCheckAuthCode(tabs: Int) = buildString {
        appendLine(tabs, "get(path) {")
        appendLine(tabs, getSingleContent(tabs + 1))
        appendLine(tabs, "}")
        appendLine(tabs, "get(\"\$path/bulk\") {")
        appendLine(tabs, getBulkContent(tabs + 1))
        appendLine(tabs, "}")
    }
    return FunSpec.builder("install" + tableTypeSpec.simpleName + "SelectBy${parameterSpec.name.capitalize()}Routes")
        .receiver(Route::class)
        .addParameter("table", tableTypeSpec)
        .addParameter("database", Database::class)
        .addParameter("path", String::class)
        .addParameter("behaviour", EndpointBehaviour::class.asTypeName().parameterizedBy(dtoClassName))
        .addCode(generateAuthCode(getCheckAuthCode(1), getCheckAuthCode(2)))
        .build()
}

fun generateUpdateRouteFunctionSpecForParam(
    dtoSpecs: DTOSpecs,
    tableTypeSpec: ClassName,
    parameterSpec: ParameterSpec,
    updateFunctionSpec: FunSpec,
): FunSpec {
    fun getSingleContent(tabs: Int) = buildString {
        appendLine()
        appendLine(tabs, "val updateDto = call.receive<${dtoSpecs.updateQueryDto.name}<${parameterSpec.type.copy(nullable = false)}>>()")
        appendLine(tabs, "newSuspendedTransaction(db = database) {")
        appendLine(tabs, "\tval intercepted = behaviour.restRepositoryInterceptor(this, updateDto.update)")
        appendLine(tabs, "\ttable.${updateFunctionSpec.name}(updateDto.query, intercepted)")
        appendLine(tabs, "}")
        appendLine(tabs, "call.respond(updateDto)")
    }

    fun getCheckAuthCode(tabs: Int) = buildString {
        appendLine(tabs, "post(path) {")
        appendLine(tabs, getSingleContent(tabs + 1))
        appendLine(tabs, "}")
    }
    return FunSpec.builder("install" + tableTypeSpec.simpleName + "UpdateBy${parameterSpec.name.capitalize()}Routes")
        .receiver(Route::class)
        .addParameter("table", tableTypeSpec)
        .addParameter("database", Database::class)
        .addParameter("path", String::class)
        .addParameter("behaviour", EndpointBehaviour::class.asTypeName().parameterizedBy(dtoSpecs.dtoClassName))
        .addCode(generateAuthCode(getCheckAuthCode(1), getCheckAuthCode(2)))
        .build()
}

fun generateRouteFunctionSpec(
    dtoClassName: ClassName,
    tableTypeSpec: ClassName,
    functions: List<FunSpec>,
    clauseName: String
) = FunSpec.builder("install${tableTypeSpec.simpleName.capitalize()}${clauseName.capitalize()}")
    .receiver(Route::class)
    .addParameter("table", tableTypeSpec)
    .addParameter("database", Database::class)
    .addParameter("path", String::class)
    .addParameter("behaviour", EndpointBehaviour::class.asTypeName().parameterizedBy(dtoClassName))
    .addCode(buildString {
        functions.forEach { appendLine("${it.name}(table, database, path, behaviour)") }
    })
    .build()

private fun generateAuthCode(authContent: String, unAuthContent: String) = buildString {
    appendLine("if (behaviour.isAuthenticated) {")
    appendLine("\tauthenticate(*behaviour.authNames.toTypedArray(), strategy = behaviour.authStrategy) {")
    appendLine(authContent)
    appendLine("\t}")
    appendLine("} else {")
    appendLine(unAuthContent)
    appendLine("}")

}

private fun String.decapitalize() = replaceFirstChar { it.lowercase(Locale.getDefault()) }

fun generateTableEndpointSetup(
    dtoClassName: ClassName,
    tableTypeSpec: ClassName,
    insertRouteInstallSpec: FunSpec,
    selectRouteInstallSpec: FunSpec,
    updateRouteInstallSpec: FunSpec,
//    deleteRouteInstallSpec: FunSpec
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
        .addCode(buildString {
            appendLine("val setup = EndpointsSetup<${dtoClassName.simpleName}>(\"${tableTypeSpec.simpleName.decapitalize()}\")")
            appendLine("\t.apply(configure)")
            appendLine("val tablePath = setup.tablePath.filter { !it.isWhitespace() }")
            appendLine("assert(tablePath.isNotEmpty()) { \"${tableTypeSpec.simpleName} path cannot be blank or empty\" }")

            appendLine("setup.configuredMethods.forEach { (endpoint, behaviour) ->")
            appendLine("\tentitiesConfigurationMap[RestRepositoriesRouteSetupKey(tablePath, endpoint)] = {")
            appendLine("\t\twhen(endpoint) {")
            appendLine("\t\t\tEndpoint.INSERT -> ${insertRouteInstallSpec.name}(table, database, tablePath, behaviour)")
            appendLine("\t\t\tEndpoint.SELECT -> ${selectRouteInstallSpec.name}(table, database, tablePath, behaviour)")
            appendLine("\t\t\tEndpoint.UPDATE -> ${updateRouteInstallSpec.name}(table, database, tablePath, behaviour)")
//            appendLine("\t\t\tEndpoint.DELETE -> ${deleteRouteInstallSpec.name}(table, database, tablePath, behaviour)")
            appendLine("\t\t}")
            appendLine("\t}")
            appendLine("}")
        })
        .build()