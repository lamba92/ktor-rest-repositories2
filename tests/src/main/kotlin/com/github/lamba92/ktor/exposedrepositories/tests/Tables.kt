package com.github.lamba92.ktor.exposedrepositories.tests

import com.github.lamba92.ktor.exposedrepositories.annotations.*
import org.jetbrains.exposed.sql.Table

@RestRepository
@RestRepositoryName("User", "Users")
object UserTable : Table() {
    val name = varchar("name", 22)
    val surname = varchar("surname", 255)
    val age = integer("age")

    @Ignore
    val email = varchar("email", 255)

    @Reference(CityTable::class, "id", "city")
    val cityId = integer("cityId").references(CityTable.id).nullable()

    @Reference(TestReferenceTable::class, "id", "test")
    val testId = integer("testId").references(TestReferenceTable.id)

    override val primaryKey = PrimaryKey(email, name = "PK_email")
}

@RestRepository
@RestRepositoryName("City", "Cities")
object CityTable : Table() {

    @AutoIncrement
    val id = integer("id").autoIncrement()

    val name = varchar("name", 50)

    @Reference(TestReferenceTable::class, "id", "test")
    val testId = integer("testId").references(TestReferenceTable.id)

    override val primaryKey = PrimaryKey(id, name = "PK_Cities_ID")
}

@RestRepository
object TestReferenceTable : Table() {
    val id = integer("id").autoIncrement()

    override val primaryKey = PrimaryKey(id)
}