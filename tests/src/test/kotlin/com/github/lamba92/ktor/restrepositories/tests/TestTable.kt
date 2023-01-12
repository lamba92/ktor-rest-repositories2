package com.github.lamba92.ktor.restrepositories.tests

import com.github.lamba92.ktor.restrepositories.annotations.RestRepository
import org.jetbrains.exposed.sql.Table

@RestRepository
class TestTable : Table() {
    val id = integer("id")
    val name = varchar("name", 255)
    val surname = varchar("surname", 255)
    val aLong = long("age")
}