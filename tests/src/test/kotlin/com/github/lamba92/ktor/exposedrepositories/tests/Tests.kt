package com.github.lamba92.ktor.exposedrepositories.tests

import com.github.lamba92.ktor.exposedrepositories.tests.dto.User
import com.github.lamba92.ktor.exposedrepositories.tests.queries.selectUserByEmail
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import kotlin.test.Test
import kotlin.test.assertEquals

class Tests {

    @Test
    fun userInsert() = restRepositoriesTestApplication { database, httpClient ->

        val httpResponse = httpClient.put("repositories/users") {
            setBody(Mocks.User.TO_INSERT)
            contentType(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, httpResponse.status)
        val responseBody = httpResponse.body<User>()
        assertEquals(Mocks.User.AFTER_INSERT, responseBody)

        val userFromDb = newSuspendedTransaction(db = database) {
            selectUserByEmail(Mocks.User.TO_INSERT.email!!)
        }

        assertEquals(Mocks.User.AFTER_INSERT, userFromDb)
    }

}

