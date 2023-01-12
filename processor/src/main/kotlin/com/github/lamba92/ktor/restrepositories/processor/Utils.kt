package com.github.lamba92.ktor.restrepositories.processor

import com.google.devtools.ksp.symbol.KSTypeReference
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import kotlin.reflect.KClass

fun FileSpec.Builder.addTypes(typeSpecs: Iterable<TypeSpec>): FileSpec.Builder {
    typeSpecs.forEach { addType(it) }
    return this
}

operator fun <T : Any> Sequence<KSTypeReference>.contains(element: KClass<T>): Boolean {
    val className = element.qualifiedName ?: return false
    return className in mapNotNull { it.resolve().declaration.qualifiedName?.getQualifier() }
}
