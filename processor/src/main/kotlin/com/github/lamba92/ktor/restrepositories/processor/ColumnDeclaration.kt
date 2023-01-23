package com.github.lamba92.ktor.restrepositories.processor

import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName

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