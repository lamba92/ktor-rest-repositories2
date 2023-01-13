package com.github.lamba92.ktor.restrepositories

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import java.sql.Connection

object TestTable : Table() {

}

data class TestTableDTO(val id: String)

class RestRepositoriesConfiguration {
    val entitiesConfigurationMap: MutableMap<Pair<String, HttpMethod>, Route.() -> Unit> = mutableMapOf()

    internal val builtRoutes
        get() = entitiesConfigurationMap.values.toList()

    var repositoryPath: String = "repositories"
        set(value) {
            assert(value.filter { !it.isWhitespace() }.isNotBlank()) { "Repository path cannot be blank or empty" }
            field = value
        }

    fun registerTestTable(database: Database,  httpMethodConf: EndpointsSetup<TestTableDTO>.() -> Unit) {

    }

    inline fun <reified T : Table> registerTable(
        table: T,
        database: Database,
        isolation: Int = Connection.TRANSACTION_REPEATABLE_READ,
        httpMethodConf: EndpointsSetup<T>.() -> Unit
    ) {
        EndpointsSetup<T>(table::class.simpleName!!.toLowerCase())
            .apply(httpMethodConf)
            .apply {
                val logBuilder = StringBuilder()
                assert(entityPath.withoutWhitespaces.isNotBlank()) { "${T::class.simpleName} path cannot be blank or empty" }
                logBuilder.appendln("Building methods for entity ${T::class.simpleName}:")
                configuredMethods.forEach { (httpMethod, behaviour) ->
                    entitiesConfigurationMap[entityPath.withoutWhitespaces to httpMethod] =
                        getDefaultBehaviour<T, K>(
                            table,
                            httpMethod,
                            database,
                            isolation,
                            behaviour.restRepositoryInterceptor
                        ).toRoute(
                            entityPath.withoutWhitespaces,
                            httpMethod,
                            behaviour.isAuthenticated,
                            behaviour.authNames
                        )
                    logBuilder.appendln(
                        "     - ${httpMethod.value.padEnd(7)} | ${repositoryPath.withoutWhitespaces}/${entityPath.withoutWhitespaces} " +
                                "| Authentication realm/s: ${behaviour.authNames.joinToString { it ?: "Default" }}"
                    )
                }
                logger.info(logBuilder.toString())
            }

    }

    class EndpointsSetup<T : Any>(
        var entityPath: String
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
            var authNames: List<String?> = mutableListOf(null),
            var restRepositoryInterceptor: RestRepositoryInterceptor<T> = { it }
        )

    }
}

typealias RestRepositoryInterceptor<T> = PipelineInterceptor<Unit, ApplicationCall>.(T) -> T

fun lol(database: Database) {
    with(RestRepositoriesConfiguration()) {
        registerTestTable(database) {
            addEndpoint(HttpMethod.Get) {
                restRepositoryInterceptor = { dto ->

                }
            }
        }
    }
}

val restRepositories
    get() = createApplicationPlugin("restRepositories", ::RestRepositoriesConfiguration) {
        application.routing {
            get {

            }
        }
    }