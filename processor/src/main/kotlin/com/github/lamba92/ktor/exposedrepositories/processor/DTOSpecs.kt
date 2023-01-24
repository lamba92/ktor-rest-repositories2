package com.github.lamba92.ktor.exposedrepositories.processor

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeSpec

data class DTOSpecs(
    val tableDeclaration: TableDeclaration,
    val dto: TypeSpec,
    val updateQueryDto: TypeSpec,
    val className: ClassName,
    val updateQueryClassName: ClassName,
    val properties: List<DTOProperty>
) {

    val parameter = ParameterSpec(className.simpleName.decapitalize(), className)

    data class WithFunctions(val specs: DTOSpecs, val functions: GeneratedQueryFunctions)
}