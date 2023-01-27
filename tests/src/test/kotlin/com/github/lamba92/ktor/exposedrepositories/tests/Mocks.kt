package com.github.lamba92.ktor.exposedrepositories.tests

import com.github.lamba92.ktor.exposedrepositories.tests.dto.City
import com.github.lamba92.ktor.exposedrepositories.tests.dto.TestReference
import com.github.lamba92.ktor.exposedrepositories.tests.dto.User
import kotlinx.serialization.json.Json

object Mocks {

    val json
        get() = Json { prettyPrint = true }

    object User {
        val TO_INSERT = User(
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

        val AFTER_INSERT =
            TO_INSERT.copy(
                city = TO_INSERT.city?.copy(id = 1, test = TO_INSERT.test?.copy(id = 1)),
                test = TO_INSERT.test?.copy(id = 2)
            )
    }
}