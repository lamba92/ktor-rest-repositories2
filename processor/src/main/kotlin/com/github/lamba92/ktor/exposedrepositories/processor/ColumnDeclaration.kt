package com.github.lamba92.ktor.exposedrepositories.processor

import com.github.lamba92.ktor.exposedrepositories.annotations.AutoIncrement
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName

sealed interface ColumnDeclaration {

    val declaration: KSPropertyDeclaration
    val resolvedType: KSType
    val simpleName: String

    val isAutoIncrement
        get() = declaration.isAnnotationPresent(AutoIncrement::class)

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