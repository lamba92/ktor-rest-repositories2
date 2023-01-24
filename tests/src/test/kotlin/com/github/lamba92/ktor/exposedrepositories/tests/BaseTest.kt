package com.github.lamba92.ktor.exposedrepositories.tests

import com.github.lamba92.ktor.exposedrepositories.tests.dto.City
import com.github.lamba92.ktor.exposedrepositories.tests.dto.TestReference
import com.github.lamba92.ktor.exposedrepositories.tests.dto.User
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.event.Level
import kotlin.test.Test

val DB_FILE_PATH: String by System.getenv()

fun getDatabase() =
    Database.connect("jdbc:h2:file:${DB_FILE_PATH}", "org.h2.Driver")

fun restRepositoriesTestApplication(
    database: Database = getDatabase(),
    isAuthenticated: Boolean = false,
    block: suspend ApplicationTestBuilder.(Database, HttpClient) -> Unit
) = testApplication {
    application {
        testServer(database, isAuthenticated)
        install(CallLogging) {
            level = Level.TRACE
        }
    }
    val httpClient = createClient {
        install(ContentNegotiation) {
            json()
        }
    }
    newSuspendedTransaction(db = database) {
        SchemaUtils.createMissingTablesAndColumns(UserTable, CityTable, TestReferenceTable)
    }
    block(database, httpClient)
}

class BaseTest {

    val TEST_USER = User(
        name = "Lamberto",
        surname = "Basti",
        age = 30,
        email = "basti.lamberto@gmail.com",
        city = City(
            name = "Pescara",
            test = TestReference()
        ),
        test = TestReference()
    )

    @Test
    fun userInsert() {
        restRepositoriesTestApplication { database, httpClient ->
            httpClient.put("repositories/users") {
                setBody(TEST_USER)
                contentType(ContentType.Application.Json)
            }
        }
    }

}