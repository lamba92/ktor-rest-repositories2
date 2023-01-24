package com.github.lamba92.ktor.exposedrepositories.processor

import com.github.lamba92.ktor.exposedrepositories.annotations.Reference
import com.github.lamba92.ktor.exposedrepositories.annotations.RestRepositoryName
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import java.util.*
import kotlin.reflect.KClass

@JvmName("containsReference")
operator fun <T : Any> Sequence<KSTypeReference>.contains(element: KClass<T>): Boolean {
    val className = element.qualifiedName ?: return false
    return className in mapNotNull { it.resolve().declaration.qualifiedName?.asString() }
}

@JvmName("containsAnnotation")
operator fun <T : Any> Sequence<KSAnnotation>.contains(element: KClass<T>): Boolean {
    val className = element.qualifiedName ?: return false
    return className in mapNotNull { it.annotationType.resolve().declaration.qualifiedName?.asString() }
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

inline fun <reified T> Sequence<KSAnnotation>.filterAnnotationsOfType() =
    filter { it.annotationType.resolve().toClassName() == T::class.asClassName() }

context(LoggerContext)
fun ColumnDeclaration(propertyDeclaration: KSPropertyDeclaration): ColumnDeclaration? {
    val resolvedType = propertyDeclaration.type.resolve()
    if (resolvedType.declaration.qualifiedName?.asString() != Column::class.qualifiedName) {
        return null
    }
    val annotationArguments = propertyDeclaration.annotations
        .filterAnnotationsOfType<Reference>()
        .firstOrNull()
        ?.arguments

    // DO NO ACCESS reference.table, it will throw because the referenced table has not yet been
    // compiled, hence there is no way to instantiate a class not yet compiled
    val annotation = propertyDeclaration.getAnnotationsByType(Reference::class).firstOrNull()
    // but you can read the source with KSP, so it's fine...
    val referencedTableClassName = (annotationArguments?.get(0)?.value as? KSType)?.toClassName()
    return if (referencedTableClassName != null && annotation != null) {
        ColumnDeclaration.WithReference(
            declaration = propertyDeclaration,
            resolvedType = resolvedType,
            reference = ColumnDeclaration.WithReference.Reference(
                tableClassName = referencedTableClassName,
                columnName = annotation.columnName,
                jsonParameterName = annotation.jsonParameterName
            )
        )
    } else ColumnDeclaration.Simple(propertyDeclaration, resolvedType)
}

data class EntityIdType(val isEntityId: Boolean, val type: KSType)

fun KSType.resolveColumnType(): EntityIdType {
    val firstLevelArgumentType = arguments.first().type!!.resolve()
    return if (firstLevelArgumentType.toClassName() == EntityID::class.asClassName())
        EntityIdType(true, firstLevelArgumentType.arguments.first().type!!.resolve())
    else EntityIdType(false, firstLevelArgumentType)
}

fun ColumnDeclaration.Simple.generateDTOPropertiesSpecs(): DTOProperty.Simple {
    val name = declaration.simpleName.asString()
    val (isEntityId, type1) = resolvedType.resolveColumnType()
    val propertySpecBuilder = PropertySpec.builder(name, type1.toTypeName().copy(nullable = true))
    val parameterSpecBuilder = ParameterSpec.builder(name, type1.toTypeName().copy(nullable = true))
        .defaultValue("null")
    propertySpecBuilder.initializer(name)
    parameterSpecBuilder.copyKotlinxSerializationAnnotations(declaration.annotations)
    return DTOProperty.Simple(
        originalColumnType = type1,
        property = propertySpecBuilder.build(),
        parameter = parameterSpecBuilder.build(),
        declaration = declaration,
        isEntityId = isEntityId
    )
}

fun ColumnDeclaration.WithReference.generateDTOPropertiesSpecs(
    referencedDtoSpec: DTOSpecs,
    referencedTableDeclaration: TableDeclaration
): DTOProperty.WithReference {
    val type = referencedDtoSpec.className.copy(nullable = true)
    val propertySpecBuilder = PropertySpec.builder(reference.jsonParameterName, type)
    val parameterSpecBuilder = ParameterSpec.builder(reference.jsonParameterName, type)
        .defaultValue("null")
    propertySpecBuilder.initializer(reference.jsonParameterName)
    val (isEntityId, type1) = resolvedType.resolveColumnType()
    return DTOProperty.WithReference(
        originalColumnType = type1,
        property = propertySpecBuilder.build(),
        parameter = parameterSpecBuilder.build(),
        declaration = declaration,
        reference = DTOProperty.WithReference.Reference(
            dtoSpec = referencedDtoSpec,
            propertyName = reference.columnName,
            referencedJsonParameterName = reference.jsonParameterName,
            tableDeclaration = referencedTableDeclaration
        ),
        isEntityId = isEntityId
    )
}

context(LoggerContext)
fun TableDeclaration.getDeclaredColumn() = declaration
    .getDeclaredProperties()
    .mapNotNull { ColumnDeclaration(it) }
    .filter { it.resolvedType.arguments.size == 1 }

context(LoggerContext)
fun Resolver.getDeclaredTables() =
    getSymbolsWithAnnotation(RestRepositoriesProcessor.RestRepositoryFQN)
        .filterIsInstance<KSClassDeclaration>()
        .filter { Table::class in it.superTypes }
        .map {
            val arguments = it.annotations
                .filterAnnotationsOfType<RestRepositoryName>()
                .firstOrNull()
                ?.arguments
            val providedSingularName = arguments
                ?.first()
                ?.value as? String
                ?: it.simpleName.asString().removeSuffix("Table")
            val providedPluralName = arguments
                ?.get(1)
                ?.value as? String
                ?: it.simpleName.asString().removeSuffix("Table").appendIfMissing("s")
            TableDeclaration(it, RestRepositoryName(providedSingularName, providedPluralName))
        }

fun String.decapitalize() = replaceFirstChar { it.lowercase(Locale.getDefault()) }


fun String.capitalize() =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

fun <T, R> R.foldOn(iterable: Iterable<T>, operation: (R, T) -> R) =
    iterable.fold(this, operation)

fun <T, R> R.foldIndexedOn(iterable: Iterable<T>, operation: (Int, R, T) -> R) =
    iterable.foldIndexed(this, operation)

fun String.appendIfMissing(ending: String) =
    if (endsWith(ending)) this else this + ending

fun TypeName.Companion.list(type: TypeName) =
    ClassName("kotlin.collections", "List").parameterizedBy(type)

fun String.appendIf(b: Boolean, s: String) =
    if (b) this + s else this

fun <T : TypeName> T.list() = TypeName.list(this)

fun CodeBlock.Builder.endControlFlow(format: String, vararg args: Any) = apply {
    unindent()
    add("}$format\n", args)
}

fun <K, V> Map<K, V>.partition(partition: (K, V) -> Boolean) =
    entries.map { Pair(it.key, it.value) }
        .partition { (k, v) -> partition(k, v) }
        .let { it.first.toMap() to it.second.toMap() }