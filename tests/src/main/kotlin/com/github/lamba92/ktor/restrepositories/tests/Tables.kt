package com.github.lamba92.ktor.restrepositories.tests

import com.github.lamba92.ktor.restrepositories.annotations.Reference
import com.github.lamba92.ktor.restrepositories.annotations.RestRepository
import com.github.lamba92.ktor.restrepositories.annotations.RestRepositoryName
import org.jetbrains.exposed.sql.Table

@RestRepository
object Users : Table() {
    val name = varchar("name", 22)
    val surname = varchar("surname", 255)
    val age = integer("age")
    val email = varchar("email", 255)

    @Reference(Cities::class, "id", "city")
    val cityId = integer("cityId").references(Cities.id).nullable()

    override val primaryKey = PrimaryKey(email, name = "PK_email")
}

@RestRepository
@RestRepositoryName("City", "Cities")
object Cities : Table() {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 50)

    override val primaryKey = PrimaryKey(id, name = "PK_Cities_ID")
}
