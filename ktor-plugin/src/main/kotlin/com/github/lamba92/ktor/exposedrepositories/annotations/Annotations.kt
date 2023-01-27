package com.github.lamba92.ktor.exposedrepositories.annotations

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import kotlin.reflect.KClass

/**
 * Marks a [Table] for generating routes for the RestRepository plugin.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class RestRepository


annotation class AutoIncrement
annotation class Ignore

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
annotation class RestRepositoryName(val singular: String, val plural: String)

/**
 * Marks a [Column] property as a foreign key for a primary key of another [Table].
 * Note, you will still need to invoke `reference` on the property such as:
 * ```kotlin
 * @RestRepository
 * object Users : Table() {
 *     val name = varchar("name", 255)
 *     val surname = varchar("surname", 255)
 *     val age = integer("age")
 *     val email = varchar("email", 255)
 *
 *     @Reference(Cities::class, "id", "city")
 *     val cityId = integer("cityId").references(Cities.id).nullable()
 *     //                                ⤷ reference is called anyway!
 *
 *     override val primaryKey = PrimaryKey(email, name = "PK_email")
 * }
 *
 * @RestRepository
 * object Cities : Table() {
 *     val id = integer("id").autoIncrement()
 *     val name = varchar("name", 50)
 *     override val primaryKey = PrimaryKey(id, name = "PK_Cities_ID")
 * }
 * ```
 * @param table The table to reference, also has to be marked with @[RestRepository]
 * @param columnName The name of the parameter in the referenced [table].
 *      NOTE: This is the name of the parameter in the Kotlin code of the table,
 *      not the name of the column in the database.
 * @param jsonParameterName the property name on the serialized JSON.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class Reference(
    val table: KClass<out Table>,
    val columnName: String,
    val jsonParameterName: String
)