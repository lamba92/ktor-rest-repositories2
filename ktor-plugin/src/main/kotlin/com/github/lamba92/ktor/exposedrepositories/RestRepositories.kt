@file:Suppress("unused")

package com.github.lamba92.ktor.exposedrepositories

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.util.*
import org.jetbrains.exposed.sql.Transaction


typealias RouteConfiguration = Route.() -> Unit

internal data class RouteToInstall(val path: String, val configuration: RouteConfiguration)

class RestRepositoriesConfiguration {
    internal val entitiesConfiguration: MutableList<RouteToInstall> = mutableListOf()

    /**
     * Adds a [RouteConfiguration] for a specific path. It is meant for
     * internal use. Please configure your custom routes manually using
     * [Application.routing] configuration block.
     */
    @InternalAPI
    fun addConfiguration(path: String, configuration: RouteConfiguration) {
        entitiesConfiguration.add(RouteToInstall(path, configuration))
    }

    /**
     * The path in which repositories will be installed. The default is `repositories`.
     */
    var repositoryPath: String = "repositories"

}

enum class Endpoint {
    INSERT, SELECT, UPDATE, DELETE
}

class EndpointsSetup<T : Any>(
    var tablePath: String
) {

    private val mutableConfiguredMethods = mutableMapOf<Endpoint, EndpointBehaviour<T>>()
    val configuredMethods
        get() = mutableConfiguredMethods.toMap()

    fun addEndpoint(endpoint: Endpoint, behaviourConfiguration: EndpointBehaviour<T>.() -> Unit = {}) {
        mutableConfiguredMethods[endpoint] = EndpointBehaviour<T>()
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

val RestRepositories
    get() = createApplicationPlugin("restRepositories", ::RestRepositoriesConfiguration) {
        val config = pluginConfig
        val repositoryPath = config.repositoryPath.filter { !it.isWhitespace() }
        require(repositoryPath.isNotEmpty()) { "Repository path is blank or empty" }
        application.routing {
            route(repositoryPath) {
                config.entitiesConfiguration.forEach { (path, configuration) ->
                    route(path, configuration)
                }
            }
        }
    }