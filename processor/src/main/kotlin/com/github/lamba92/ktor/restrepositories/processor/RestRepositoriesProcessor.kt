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
            val tableTypeSpec = tableClassDeclaration.toClassName()
            writeDto(
                generatedPackageName = generatedPackageName,
                dtoClassName = dtoClassName,
                dtoSpec = dtoSpec,
                originatingFile = originatingFile
            )
            val functions = writeFunctions(
                generatedPackageName = generatedPackageName,
                dtoClassName = dtoClassName,
                allProperties = dtoPropertiesSpecs.map { it.parameters },
                tableClassName = tableTypeSpec,
                originatingFile = originatingFile
            )
            writeRoutes(
                generatedPackageName = generatedPackageName,
                tableClassName = tableTypeSpec,
                dtoSpec = dtoClassName,
                tableTypeSpec = tableTypeSpec,
                insert = functions.insert,
                originatingFile = originatingFile
            )

        }

        return emptyList()
    }

    private fun writeRoutes(
        dtoSpec: ClassName,
        tableTypeSpec: ClassName,
        insert: FunSpec,
        generatedPackageName: String,
        tableClassName: ClassName,
        originatingFile: KSFile,
    ) =
        FileSpec.builder("$generatedPackageName.queries", "${tableClassName.simpleName}Routes")
            .addImport(dtoSpec.packageName, dtoSpec.simpleName)
            .addImport(
                "org.jetbrains.exposed.sql.transactions.experimental",
                "newSuspendedTransaction"
            )
            .addImport("io.ktor.server.application", "call")
            .addImport("io.ktor.server.auth", "authenticate")
            .addImport("io.ktor.server.request", "receive")
            .addImport("io.ktor.server.response", "respond")
            .addImport("io.ktor.server.routing", "Route", "get")
            .addFunction(generateInsertRouteFunctionSpec(dtoSpec, tableTypeSpec, insert))
            .build()
            .writeTo(codeGenerator, Dependencies(false, originatingFile))

    private fun writeFunctions(
        generatedPackageName: String,
        dtoClassName: ClassName,
        allProperties: List<ParameterSpec>,
        tableClassName: ClassName,
        originatingFile: KSFile
    ): GeneratedFunctions {
        val functions = GeneratedFunctions(
            generateInsert(dtoClassName, allProperties, tableClassName),
            allProperties.associateWith { generateSelectBySingleProperty(dtoClassName, it, allProperties, tableClassName) },
            allProperties.associateWith { generateSelectByMultipleProperties(dtoClassName, it, allProperties, tableClassName) },
            allProperties.associateWith { generateDeleteBySingleProperty(it, tableClassName) },
            allProperties.associateWith { generateUpdateBySingleProperty(dtoClassName, it, allProperties, tableClassName) },
        )
        FileSpec.builder("$generatedPackageName.queries", "${tableClassName.simpleName}Queries")
            .addImport(
                "org.jetbrains.exposed.sql",
                "insert", "Transaction", "select", "deleteWhere", "update"
            )
            .addImport(dtoClassName.packageName, dtoClassName.simpleName)
            .addFunction(functions.insert)
            .foldOn(functions.selectBySingle.values) { acc, spec -> acc.addFunction(spec) }
            .foldOn(functions.delete.values) { acc, spec -> acc.addFunction(spec) }
            .foldOn(functions.selectByMultiple.values) { acc, spec -> acc.addFunction(spec) }
            .build()
            .writeTo(codeGenerator, Dependencies(false, originatingFile))
        return functions
    }

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






