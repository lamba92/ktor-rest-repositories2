package com.github.lamba92.ktor.restrepositories.processor

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toTypeName
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

fun Sequence<ColumnDeclaration>.generateDTOPropertiesSpecs() =
    map { (propertyDeclaration: KSPropertyDeclaration, resolvedType: KSType) ->
        val name = propertyDeclaration.simpleName.asString()
        val type = resolvedType.arguments.first().type!!.resolve()
        val propertySpecBuilder = PropertySpec.builder(name, type.toTypeName().copy(nullable = true))
        val parameterSpecBuilder = ParameterSpec.builder(name, type.toTypeName().copy(nullable = true))
            .defaultValue("null")

        parameterSpecBuilder.copyKotlinxSerializationAnnotations(propertyDeclaration.annotations)
        propertySpecBuilder.initializer(name)
        DTOPropertiesSpec(type, propertySpecBuilder.build(), parameterSpecBuilder.build())
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

fun String.decapitalize() = replaceFirstChar { it.lowercase(Locale.getDefault()) }


fun String.capitalize() =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

fun <T, R> R.foldOn(iterable: Iterable<T>, operation: (R, T) -> R) =
    iterable.fold(this, operation)

fun <T, R> R.foldIndexedOn(iterable: Iterable<T>, operation: (Int, R, T) -> R) =
    iterable.foldIndexed(this, operation)

fun String.appendIfMissing(ending: String) =
    if (endsWith(ending)) this else this + ending
data class GeneratedQueryFunctions(
    val insertSingle: FunSpec,
    val insertBulk: FunSpec,
    val selectBySingle: Map<ParameterSpec, FunSpec>,
    val selectByMultiple: Map<ParameterSpec, FunSpec>,
    val delete: Map<ParameterSpec, FunSpec>,
    val update: Map<ParameterSpec, FunSpec>
)

fun TypeName.Companion.list(type: TypeName) =
    ClassName("kotlin.collections", "List").parameterizedBy(type)

fun String.appendIf(b: Boolean, s: String) =
    if (b) this + s else this