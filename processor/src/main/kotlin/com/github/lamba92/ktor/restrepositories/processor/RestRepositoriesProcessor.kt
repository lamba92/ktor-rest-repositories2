package com.github.lamba92.ktor.restrepositories.processor

import com.github.lamba92.ktor.restrepositories.annotations.RestRepository
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import org.jetbrains.exposed.sql.Table

class RestRepositoriesProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    companion object {
        val RestRepositoryFQN = RestRepository::class.qualifiedName!!
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.forEachDeclaredTable { tableClassDeclaration: KSClassDeclaration ->
            val originatingFile = tableClassDeclaration.containingFile ?: return@forEachDeclaredTable
            val columnDeclarations = tableClassDeclaration.getDeclaredColumn()
            val dtoPropertiesSpecs = columnDeclarations
                .generateDTOPropertiesSpecs()
                .toList()
            val generatedPackageName = tableClassDeclaration.packageName.asString()
            val dtoClassName = ClassName(
                packageName = "$generatedPackageName.dto",
                "${tableClassDeclaration.simpleName.asString().removeSuffix("s")}DTO"
            )
            val dtoSpec = generateDto(dtoClassName, dtoPropertiesSpecs)
            writeDto(generatedPackageName, dtoClassName, dtoSpec, originatingFile)
            writeFunctions(
                generatedPackageName,
                dtoClassName,
                dtoPropertiesSpecs.map { it.parameters },
                tableClassDeclaration.toClassName(),
                originatingFile
            )
        }

        return emptyList()
    }

    private fun writeFunctions(
        generatedPackageName: String,
        dtoClassName: ClassName,
        allProperties: List<ParameterSpec>,
        tableClassName: ClassName,
        originatingFile: KSFile
    ) = FileSpec.builder("$generatedPackageName.queries", "${tableClassName.simpleName}Queries")
        .addImport(
            "org.jetbrains.exposed.sql",
            "insert", "Transaction", "select", "deleteWhere", "update"
        )
        .addImport(dtoClassName.packageName, dtoClassName.simpleName)
        .addFunction(generateInsert(dtoClassName, allProperties, tableClassName))
        .foldOn(allProperties) { acc, parameterSpec ->
            acc.addFunction(generateSelectBySingleProperty(dtoClassName, parameterSpec, allProperties, tableClassName))
                .addFunction(generateSelectByMultipleProperties(dtoClassName, parameterSpec, allProperties, tableClassName))
                .addFunction(generateDeleteBySingleProperty(parameterSpec, tableClassName))
                .addFunction(generateUpdateBySingleProperty(dtoClassName, parameterSpec, allProperties, tableClassName))
        }
        .build()
        .writeTo(codeGenerator, Dependencies(false, originatingFile))

    private fun writeDto(
        generatedPackageName: String,
        dtoClassName: ClassName,
        dtoSpec: TypeSpec,
        originatingFile: KSFile
    ) = FileSpec.builder("$generatedPackageName.dto", dtoClassName.simpleName)
        .addType(dtoSpec)
        .build()
        .writeTo(codeGenerator, Dependencies(false, originatingFile))

}



object Users : Table() {
    val id = varchar("id", 10) // Column<String>
    val name = varchar("name", length = 50) // Column<String>
//    val cityId = (integer("city_id") references Cities.id).nullable() // Column<Int?>
}






