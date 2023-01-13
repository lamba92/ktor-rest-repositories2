package com.github.lamba92.ktor.restrepositories.processor

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toTypeName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import java.util.*
import kotlin.reflect.KClass

operator fun <T : Any> Sequence<KSTypeReference>.contains(element: KClass<T>): Boolean {
    val className = element.qualifiedName ?: return false
    return className in mapNotNull { it.resolve().declaration.qualifiedName?.asString() }
}

fun ParameterSpec.Builder.copyKotlinxSerializationAnnotations(annotations: Sequence<KSAnnotation>) {
    annotations.filter {
        val fqn = it.annotationType.resolve().declaration.qualifiedName?.asString()
            ?: return@filter false
        "kotlinx.serialization" in fqn
    }
        .forEach {
            val annotationSpec = it.toAnnotationSpec()
            addAnnotation(annotationSpec)
        }
}


data class ColumnDeclaration(val propertyDeclaration: KSPropertyDeclaration, val resolvedType: KSType)
data class DTOPropertiesSpec(val properties: PropertySpec, val parameters: ParameterSpec)

fun Sequence<ColumnDeclaration>.generateDTOPropertiesSpecs() =
    map { (propertyDeclaration: KSPropertyDeclaration, resolvedType: KSType) ->
        val name = propertyDeclaration.simpleName.asString()
        val type = resolvedType.arguments.first().type!!.resolve().toTypeName()
            .copy(nullable = true)
        val propertySpecBuilder = PropertySpec.builder(name, type)
        val parameterSpecBuilder = ParameterSpec.builder(name, type)
            .defaultValue("null")

        parameterSpecBuilder.copyKotlinxSerializationAnnotations(propertyDeclaration.annotations)
        propertySpecBuilder.initializer(name)
        DTOPropertiesSpec(propertySpecBuilder.build(), parameterSpecBuilder.build())
    }

fun KSClassDeclaration.getDeclaredColumn() = getDeclaredProperties()
    .map { ColumnDeclaration(it, it.type.resolve()) }
    .filter { it.resolvedType.declaration.qualifiedName?.asString() == Column::class.qualifiedName }
    .filter { it.resolvedType.arguments.size == 1 }

fun Resolver.forEachDeclaredTable(function: (KSClassDeclaration) -> Unit) =
    getSymbolsWithAnnotation(RestRepositoriesProcessor.RestRepositoryFQN)
        .filterIsInstance<KSClassDeclaration>()
        .filter { Table::class in it.superTypes }
        .forEach(function)

fun String.capitalize() =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

fun <T> FileSpec.Builder.foldOn(iterables: Iterable<T>, action: (FileSpec.Builder, T) -> FileSpec.Builder) =
    iterables.fold(this, action)
fun generateDto(
    dtoClassName: ClassName,
    properties: List<DTOPropertiesSpec>
) = TypeSpec.classBuilder(dtoClassName)
    .addModifiers(KModifier.DATA)
    .addAnnotation(Serializable::class)
    .primaryConstructor(
        FunSpec.constructorBuilder()
            .addParameters(properties.map { it.parameters })
            .build()
    )
    .addProperties(properties.map { it.properties })
    .build()
