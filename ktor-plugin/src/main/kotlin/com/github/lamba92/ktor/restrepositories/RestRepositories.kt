package com.github.lamba92.ktor.restrepositories

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection

object TestTable : Table() {

}

data class TestTableDTO(val id: String)
data class RestRepositoriesRouteSetupKey(val path: String, val method: HttpMethod)

class RestRepositoriesConfiguration {
    val entitiesConfigurationMap: MutableMap<RestRepositoriesRouteSetupKey, Route.() -> Unit> = mutableMapOf()

    internal val builtRoutes
        get() = entitiesConfigurationMap.values.toList()

    var repositoryPath: String = "repositories"
        set(value) {
            assert(value.filter { !it.isWhitespace() }.isNotBlank()) { "Repository path cannot be blank or empty" }
            field = value
        }

    fun registerTestTable(database: Database,  configure: EndpointsSetup<TestTableDTO>.() -> Unit) {
        val setup = EndpointsSetup<TestTableDTO>("whaever")
            .apply(configure)


    }

    inline fun <reified T : Table> registerTable(
        table: T,
        database: Database,
        isolation: Int = Connection.TRANSACTION_REPEATABLE_READ,
        httpMethodConf: EndpointsSetup<T>.() -> Unit
    ) {
//        EndpointsSetup<T>(table::class.simpleName!!.toLowerCase())
//            .apply(httpMethodConf)
//            .apply {
//                val logBuilder = StringBuilder()
//                assert(tablePath.withoutWhitespaces.isNotBlank()) { "${T::class.simpleName} path cannot be blank or empty" }
//                logBuilder.appendln("Building methods for entity ${T::class.simpleName}:")
//                configuredMethods.forEach { (httpMethod, behaviour) ->
//                    entitiesConfigurationMap[tablePath.withoutWhitespaces to httpMethod] =
//                        getDefaultBehaviour<T, K>(
//                            table,
//                            httpMethod,
//                            database,
//                            isolation,
//                            behaviour.restRepositoryInterceptor
//                        ).toRoute(
//                            tablePath.withoutWhitespaces,
//                            httpMethod,
//                            behaviour.isAuthenticated,
//                            behaviour.authNames
//                        )
//                    logBuilder.appendln(
//                        "     - ${httpMethod.value.padEnd(7)} | ${repositoryPath.withoutWhitespaces}/${tablePath.withoutWhitespaces} " +
//                                "| Authentication realm/s: ${behaviour.authNames.joinToString { it ?: "Default" }}"
//                    )
//                }
//                logger.info(logBuilder.toString())
//            }

    }

    class EndpointsSetup<T : Any>(
        var tablePath: String
    ) {

        val configuredMethods = mutableMapOf<HttpMethod, Behaviour<T>>()

        fun addEndpoint(httpMethod: HttpMethod, behaviourConfiguration: Behaviour<T>.() -> Unit = {}) {
            configuredMethods[httpMethod] = Behaviour<T>()
                .apply(behaviourConfiguration)
        }

        fun addEndpoints(vararg httpMethods: HttpMethod, behaviourConfiguration: Behaviour<T>.() -> Unit = {}) =
            httpMethods.forEach { addEndpoint(it, behaviourConfiguration) }

        data class Behaviour<T>(
            var isAuthenticated: Boolean = false,
            val authStrategy: AuthenticationStrategy = AuthenticationStrategy.Optional,
            var authNames: List<String?> = mutableListOf(null),
            var restRepositoryInterceptor: RestRepositoryInterceptor<T> = { it }
        )

    }
}

typealias RestRepositoryInterceptor<T> = suspend Transaction.(T) -> T

suspend fun Route.lol(database: Database, list: List<String>) {
    newSuspendedTransaction {  }
}

val restRepositories
    get() = createApplicationPlugin("restRepositories", ::RestRepositoriesConfiguration) {
        application.routing {
            get {

            }
        }
    }