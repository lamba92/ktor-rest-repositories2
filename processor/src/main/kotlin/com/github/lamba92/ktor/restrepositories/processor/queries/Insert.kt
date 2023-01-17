package com.github.lamba92.ktor.restrepositories.processor.queries

import com.github.lamba92.ktor.restrepositories.processor.*
import com.squareup.kotlinpoet.*
import org.jetbrains.exposed.sql.Transaction

/*
A.insert {
    dto.a?.let { aNew -> it[a] = aNew }
    dto.b?.let { bNew -> it[b] = bNew.id }
}
dto.b?.let { bNew -> B.insert(b) }
 */
data class PropertyQueueElement(
    val dtoProperty: DTOProperty.WithReference,
    val specWithFunctions: DTOSpecs.WithFunctions
)

fun generateSingleInsert(
    dtoSpecs: DTOSpecs,
    map: MutableMap<TableDeclaration, DTOSpecs.WithFunctions>
): FunSpec {
    val mainTableParamName = dtoSpecs.tableDeclaration.className.simpleName.decapitalize()
    val funSpec = FunSpec.builder("insert")
        .contextReceivers(Transaction::class.asTypeName())
        .addParameter(mainTableParamName, dtoSpecs.tableDeclaration.className)
        .addParameter(ParameterSpec.builder("dto", dtoSpecs.dtoClassName).build())
    val queue = mutableListOf<PropertyQueueElement>()
    val codeBlock = CodeBlock.builder()
        .beginControlFlow("val insertStatement = mainTableParamName.insert {")
        .foldOn(dtoSpecs.properties.values) { acc, dtoProperty ->
            val cName = dtoProperty.parameter.name.capitalize()
            when (dtoProperty) {
                is DTOProperty.Simple -> acc.addStatement(
                    format = "dto.%N?.let { dto%L -> it[%N] = dto%L }",
                    dtoProperty, cName, dtoProperty, cName
                )

                is DTOProperty.WithReference -> {
                    queue.add(
                        PropertyQueueElement(
                            dtoProperty = dtoProperty,
                            specWithFunctions = map.getValue(dtoProperty.reference.tableDeclaration)
                        )
                    )
                    acc.addStatement(
                        format = "dto.%N?.let { dto%L -> it[%N] = dto%L.%N }",
                        dtoProperty, cName, dtoProperty, dtoProperty.declaration,
                        dtoProperty.reference.referencedJsonParameterName
                    )
                }
            }
        }
        .endControlFlow()

    queue.forEach { (dtoProperty, specsWithFunctions) ->
        val tableParamSpec = ParameterSpec(
            name = specsWithFunctions.specs.tableDeclaration.className.simpleName.decapitalize(),
            type = specsWithFunctions.specs.tableDeclaration.className
        )
        funSpec
            .addParameter(tableParamSpec)
        codeBlock
            .addStatement(
                "val %L = dto.%N?.let { %N.%N(it) }",
                dtoProperty.reference.referencedJsonParameterName, dtoProperty.property,
                specsWithFunctions.specs.dtoClassName, specsWithFunctions.functions.insertSingle
            )
    }
    codeBlock
        .addStatement("return %T(", dtoSpecs.dtoClassName)
        .foldIndexedOn(dtoSpecs.properties.values) { index, acc, next ->
            acc.addStatement(
                format = "\t%N = insertStatement[%N]".appendIf(dtoSpecs.properties.size - 1 != index, ","),
                next, next
            )
        }

    if (queue.isNotEmpty()) codeBlock.add(",")
    queue.forEachIndexed { index, (dtoProperty, specsWithFunctions) ->
        codeBlock.addStatement(
            "\t%N = %L".appendIf(queue.lastIndex == index, ","),
            dtoProperty.property, dtoProperty.reference.referencedJsonParameterName
        )
    }
    codeBlock.addStatement(")")

    return funSpec
        .addCode(codeBlock.build())
        .returns(dtoSpecs.dtoClassName)
        .build()
}

fun generateBulkInsert(
    dtoSpecs: DTOSpecs,
    allProperties: Map<TableDeclaration, DTOSpecs.WithFunctions>,
): FunSpec {
    val returnType = TypeName.list(dtoSpecs)
    return FunSpec.builder("insert")
        .contextReceivers(Transaction::class.asTypeName())
        .receiver(dtoSpecs.tableDeclaration.className)
        .addParameter(ParameterSpec.builder("dtos", returnType).build())
        .addCode(
            CodeBlock.builder()
                .beginControlFlow("return dtos.map { dto ->")
                .beginControlFlow("val insertStatement = insert {")
                .foldOn(allProperties) { acc, next ->
                    val cName = next.name.capitalize()
                    acc.addStatement("dto.%N?.let { dto%L -> it[%N] = dto%L }", next, cName, next, cName)
                }
                .endControlFlow()
                .addStatement("%T(", dtoSpecs)
                .foldIndexedOn(allProperties) { index, acc, next ->
                    acc.addStatement(
                        format = "\t%N = insertStatement[%N]".appendIf(index != allProperties.lastIndex, ","),
                        next, next
                    )
                }
                .addStatement(")")
                .endControlFlow()
                .build()
        )
        .returns(returnType)
        .build()
}