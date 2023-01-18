package com.github.lamba92.ktor.restrepositories.processor

import com.github.lamba92.ktor.restrepositories.annotations.Reference
import com.github.lamba92.ktor.restrepositories.annotations.RestRepositoryName
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
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
fun ColumnDeclaration(propertyDeclaration: KSPropertyDeclaration): ColumnDeclaration {
    val resolvedType = propertyDeclaration.type.resolve()
    val annotationArguments = propertyDeclaration.annotations.filterAnnotationsOfType<Reference>()
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
    }
    else ColumnDeclaration.Simple(propertyDeclaration, resolvedType)
}

sealed interface ColumnDeclaration {

    val declaration: KSPropertyDeclaration
    val resolvedType: KSType
    val simpleName: String

    data class Simple(
        override val declaration: KSPropertyDeclaration,
        override val resolvedType: KSType,
        override val simpleName: String = declaration.simpleName.asString()
    ) : ColumnDeclaration

    data class WithReference(
        override val declaration: KSPropertyDeclaration,
        override val resolvedType: KSType,
        val reference: Reference,
        override val simpleName: String = declaration.simpleName.asString()
    ) : ColumnDeclaration {
        data class Reference(
            val tableClassName: ClassName,
            val columnName: String,
            val jsonParameterName: String
        )
    }
}

fun ColumnDeclaration.Simple.generateDTOPropertiesSpecs(): DTOProperty.Simple {
    val name = declaration.simpleName.asString()
    val type1 = resolvedType.arguments.first().type!!.resolve()
    val propertySpecBuilder = PropertySpec.builder(name, type1.toTypeName().copy(nullable = true))
    val parameterSpecBuilder = ParameterSpec.builder(name, type1.toTypeName().copy(nullable = true))
        .defaultValue("null")
    propertySpecBuilder.initializer(name)
    parameterSpecBuilder.copyKotlinxSerializationAnnotations(declaration.annotations)
    return DTOProperty.Simple(type1, propertySpecBuilder.build(), parameterSpecBuilder.build(), declaration)
}

fun ColumnDeclaration.WithReference.generateDTOPropertiesSpecs(
    referencedDtoSpec: DTOSpecs,
    referencedTableDeclaration: TableDeclaration
): DTOProperty.WithReference {
    val type = referencedDtoSpec.dtoClassName.copy(nullable = true)
    val propertySpecBuilder = PropertySpec.builder(reference.jsonParameterName, type)
    val parameterSpecBuilder = ParameterSpec.builder(reference.jsonParameterName, type)
        .defaultValue("null")
    propertySpecBuilder.initializer(reference.jsonParameterName)
    return DTOProperty.WithReference(
        originalColumnType = resolvedType.arguments.first().type!!.resolve(),
        property = propertySpecBuilder.build(),
        parameter = parameterSpecBuilder.build(),
        declaration = declaration,
        reference = DTOProperty.WithReference.Reference(
            dtoSpec = referencedDtoSpec,
            propertyName = reference.columnName,
            referencedJsonParameterName = reference.jsonParameterName,
            tableDeclaration = referencedTableDeclaration
        )
    )
}

context(LoggerContext)
fun TableDeclaration.getDeclaredColumn() = declaration
    .getDeclaredProperties()
    .map { ColumnDeclaration(it) }
    .filter { it.resolvedType.declaration.qualifiedName?.asString() == Column::class.qualifiedName }
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
                ?.first()
                ?.value as? String
                ?: it.simpleName.asString().appendIfMissing("Table")
            TableDeclaration(it, providedSingularName, providedPluralName)
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

data class GeneratedQueryFunctions(
    val insertSingle: FunSpec,
    val insertBulk: FunSpec,
    val selectBySingle: Map<String, FunSpec>,
//    val selectByMultiple: Map<ParameterSpec, FunSpec>,
//    val delete: Map<ParameterSpec, FunSpec>,
//    val update: Map<ParameterSpec, FunSpec>
)

fun TypeName.Companion.list(type: TypeName) =
    ClassName("kotlin.collections", "List").parameterizedBy(type)

fun String.appendIf(b: Boolean, s: String) =
    if (b) this + s else this

fun <T : TypeName> T.list() = TypeName.list(this)
fun ClassName.asParameter() = ParameterSpec(simpleName.decapitalize(), this)