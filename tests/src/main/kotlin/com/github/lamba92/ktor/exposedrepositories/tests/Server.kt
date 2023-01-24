package com.github.lamba92.ktor.exposedrepositories.tests

import com.github.lamba92.ktor.exposedrepositories.Endpoint.*
import com.github.lamba92.ktor.exposedrepositories.RestRepositories
import com.github.lamba92.ktor.exposedrepositories.tests.routes.registerCityTable
import com.github.lamba92.ktor.exposedrepositories.tests.routes.registerTestReferenceTable
import com.github.lamba92.ktor.exposedrepositories.tests.routes.registerUserTable
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import org.jetbrains.exposed.sql.Database

fun Application.testServer(database: Database, isAuthenticated: Boolean) {
    install(ContentNegotiation) {
        json()
    }
    install(RestRepositories) {
        repositoryPath = "api"
        registerCityTable(database) {
            tablePath = "myCities"
            addEndpoint(INSERT) {

            }
            addEndpoints(INSERT, SELECT, UPDATE, DELETE) {
                this.isAuthenticated = isAuthenticated
                authNames = listOf()
            }
        }
        registerUserTable(database) {
            addEndpoints(INSERT, SELECT, UPDATE, DELETE) {
                this.isAuthenticated = isAuthenticated
            }
        }
        registerTestReferenceTable(database) {
            addEndpoints(INSERT, SELECT, UPDATE, DELETE) {
                this.isAuthenticated = isAuthenticated
            }
        }
    }
}
