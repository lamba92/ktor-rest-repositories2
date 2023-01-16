package com.github.lamba92.ktor.restrepositories.processor

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.*
import kotlinx.serialization.Serializable

data class DTOPropertiesSpec(val originalColumnType: KSType, val property: PropertySpec, val parameter: ParameterSpec)

fun generateDtos(
    dtoClassName: ClassName,
    updateQueryDtoClassName: ClassName,
    properties: List<DTOPropertiesSpec>
): DTOSpecs {
    val dto = TypeSpec.classBuilder(dtoClassName)
        .addModifiers(KModifier.DATA)
        .addAnnotation(Serializable::class)
        .primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameters(properties.map { it.parameter })
                .build()
        )
        .addProperties(properties.map { it.property })
        .build()
    val typeVariable = TypeVariableName("T")
    val queryParamSpec = ParameterSpec("query", typeVariable)
    val queryPropertySpec = PropertySpec.builder("query", typeVariable)
        .initializer("query")
        .build()
    val updateParamSpec = ParameterSpec("update", dtoClassName)
    val updatePropertySpec = PropertySpec.builder("update", dtoClassName)
        .initializer("update")
        .build()
    val updateDto = TypeSpec.classBuilder(updateQueryDtoClassName)
        .addModifiers(KModifier.DATA)
        .addTypeVariable(typeVariable)
        .addAnnotation(Serializable::class)
        .primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter(queryParamSpec)
                .addParameter(updateParamSpec)
                .build()
        )
        .addProperty(queryPropertySpec)
        .addProperty(updatePropertySpec)
        .build()


    return DTOSpecs(dto, updateDto, dtoClassName, updateQueryDtoClassName)
}

data class DTOSpecs(
    val dto: TypeSpec,
    val updateQueryDto: TypeSpec,
    val dtoClassName: ClassName,
    val updateQueryDtoClassName: ClassName
)