package com.github.lamba92.ktor.exposedrepositories.tests

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.testing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.event.Level
import java.nio.file.Path
import kotlin.io.path.deleteRecursively

val DB_FILE_PATH: String by System.getenv()
fun getDatabase() =
    Database.connect("jdbc:h2:file:$DB_FILE_PATH", "org.h2.Driver")

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
    withContext(Dispatchers.IO) {
        Path.of(DB_FILE_PATH).parent.deleteRecursively()
    }
    newSuspendedTransaction(db = database) {
        SchemaUtils.createMissingTablesAndColumns(UserTable, CityTable, TestReferenceTable)
    }
    block(database, httpClient)
}