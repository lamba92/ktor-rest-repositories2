package com.github.lamba92.ktor.restrepositories

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.Transaction


data class RestRepositoriesRouteSetupKey(val path: String, val method: Endpoint)

class RestRepositoriesConfiguration {
    val entitiesConfigurationMap: MutableMap<RestRepositoriesRouteSetupKey, Route.() -> Unit> = mutableMapOf()

    internal val builtRoutes
        get() = entitiesConfigurationMap.values.toList()

    var repositoryPath: String = "repositories"
        set(value) {
            assert(value.filter { !it.isWhitespace() }.isNotBlank()) { "Repository path cannot be blank or empty" }
            field = value
        }

}

enum class Endpoint {
    INSERT, SELECT, UPDATE, DELETE
}

class EndpointsSetup<T : Any>(
    var tablePath: String
) {

    val configuredMethods = mutableMapOf<Endpoint, EndpointBehaviour<T>>()

    fun addEndpoint(endpoint: Endpoint, behaviourConfiguration: EndpointBehaviour<T>.() -> Unit = {}) {
        configuredMethods[endpoint] = EndpointBehaviour<T>()
            .apply(behaviourConfiguration)
    }

    fun addEndpoints(vararg endpoints: Endpoint, behaviourConfiguration: EndpointBehaviour<T>.() -> Unit = {}) =
        endpoints.forEach { addEndpoint(it, behaviourConfiguration) }


}

data class EndpointBehaviour<T>(
    var isAuthenticated: Boolean = false,
    val authStrategy: AuthenticationStrategy = AuthenticationStrategy.Optional,
    var authNames: List<String?> = mutableListOf(null),
    var restRepositoryInterceptor: RestRepositoryInterceptor<T> = { it }
)

typealias RestRepositoryInterceptor<T> = suspend Transaction.(T) -> T

val restRepositories
    get() = createApplicationPlugin("restRepositories", ::RestRepositoriesConfiguration) {
        application.routing {
            get {

            }
        }
    }