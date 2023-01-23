package com.github.lamba92.ktor.restrepositories.processor

import com.github.lamba92.ktor.restrepositories.annotations.RestRepositoryName
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ksp.toClassName

data class TableDeclaration(
    val declaration: KSClassDeclaration,
    val names: RestRepositoryName
) {

    val className: ClassName = declaration.toClassName()

    val parameter = ParameterSpec.builder(className.simpleName.decapitalize(), className)
        .let {
            if (declaration.classKind == ClassKind.OBJECT) it.defaultValue("%T", declaration.toClassName())
            else it
        }
        .build()
}