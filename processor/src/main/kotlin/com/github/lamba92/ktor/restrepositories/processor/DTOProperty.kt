package com.github.lamba92.ktor.restrepositories.processor

import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec

sealed interface DTOProperty {

    val originalColumnType: KSType
    val property: PropertySpec
    val parameter: ParameterSpec
    val declaration: KSPropertyDeclaration
    val declarationSimpleName: String

    data class Simple(
        override val originalColumnType: KSType,
        override val property: PropertySpec,
        override val parameter: ParameterSpec,
        override val declaration: KSPropertyDeclaration,
        override val declarationSimpleName: String = declaration.simpleName.asString()
    ) : DTOProperty

    data class WithReference(
        override val originalColumnType: KSType,
        override val property: PropertySpec,
        override val parameter: ParameterSpec,
        override val declaration: KSPropertyDeclaration,
        val reference: Reference,
        override val declarationSimpleName: String = declaration.simpleName.asString()
    ) : DTOProperty {
        data class Reference(
            val dtoSpec: DTOSpecs,
            val propertyName: String,
            val tableDeclaration: TableDeclaration,
            val referencedJsonParameterName: String,
        )
    }
}