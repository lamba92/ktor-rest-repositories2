package com.github.lamba92.ktor.restrepositories.processor

import com.github.lamba92.ktor.restrepositories.annotations.RestRepository
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

class RestRepositoriesProcessor(private val codeGenerator: CodeGenerator, private val logger: KSPLogger) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        logger.warn("HELLO!")
        resolver.getSymbolsWithAnnotation(RestRepository::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()
            .filter { Table::class in it.superTypes }
            .forEach { tableClassDeclaration ->
                val originatingFile = tableClassDeclaration.containingFile ?: return@forEach
                val tableName = tableClassDeclaration.simpleName.asString()
                val properties = tableClassDeclaration.getAllProperties()
                    .map { it.type.resolve() }
                    .filter { it.declaration.qualifiedName?.asString() == Column::class.qualifiedName }
                    .filter { it.arguments.size == 1 }
                    .map {
                        PropertySpec.Companion.builder(
                            it.declaration.simpleName.asString(),
                            it.arguments.first().type!!.resolve().toClassName()
                        ).build()
                    }
                    .toList()
                val dtoClassName = ClassName(
                    packageName = tableClassDeclaration.packageName.asString(),
                    "${tableClassDeclaration.simpleName.asString()}DTO"
                )

                val classSpec = TypeSpec.classBuilder(dtoClassName)
                    .addAnnotation(Serializable::class)
                    .addProperties(properties)
                    .build()

                FileSpec.builder(dtoClassName.packageName, dtoClassName.simpleName)
                    .addType(classSpec)
                    .build()
                    .writeTo(codeGenerator, Dependencies(false, originatingFile))
            }

        return emptyList()
    }
}