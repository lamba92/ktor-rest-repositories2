package com.github.lamba92.ktor.restrepositories.tests

import com.github.lamba92.ktor.restrepositories.annotations.RestRepository
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.exposed.sql.Table

@RestRepository
class TestTable : Table() {
    val id = integer("id")
    val name = varchar("name", 255)

    val surname = varchar("surname", 255).nullable()

    @Serializable(with = LSerializer::class)
    val aLong = long("age")
}

@RestRepository
class UsersTable : Table() {
    val id = integer("id")
    val name = varchar("name", 255)

    val surname = varchar("surname", 255).nullable()
}

object LSerializer : KSerializer<Long> {

    override val descriptor: SerialDescriptor
        get() = TODO("Not yet implemented")

    override fun deserialize(decoder: Decoder): Long {
        TODO("Not yet implemented")
    }

    override fun serialize(encoder: Encoder, value: Long) {
        TODO("Not yet implemented")
    }

}