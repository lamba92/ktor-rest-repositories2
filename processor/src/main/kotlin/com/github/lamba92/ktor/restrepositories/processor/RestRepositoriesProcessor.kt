package com.github.lamba92.ktor.restrepositories.processor

import com.github.lamba92.ktor.restrepositories.RestRepositoriesConfiguration.EndpointsSetup
import com.github.lamba92.ktor.restrepositories.RestRepositoriesRouteSetupKey
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
            val allProperties = dtoPropertiesSpecs.map { it.parameters }
            val functions = writeFunctions(
                generatedPackageName = generatedPackageName,
                dtoClassName = dtoClassName,
                allProperties = allProperties,
                tableClassName = tableTypeSpec,
                originatingFile = originatingFile
            )
            writeRoutes(
                dtoSpec = dtoClassName,
                tableTypeSpec = tableTypeSpec,
                generatedFunctions = functions,
                generatedPackageName = generatedPackageName,
                tableClassName = tableTypeSpec,
                originatingFile = originatingFile,
            )

        }

        return emptyList()
    }

    private fun writeRoutes(
        dtoSpec: ClassName,
        tableTypeSpec: ClassName,
        generatedFunctions: GeneratedFunctions,
        generatedPackageName: String,
        tableClassName: ClassName,
        originatingFile: KSFile,
    ) {
        val insertRouteInstallSpec = generateInsertRouteFunctionSpec(
            dtoSpec = dtoSpec,
            tableTypeSpec = tableTypeSpec,
            insertSingle = generatedFunctions.insertSingle,
            insertBulk = generatedFunctions.insertBulk
        )
        val selectRouteInstallSpecs = generatedFunctions.selectBySingle.keys
            .map {
                generateSelectRouteFunctionSpecForParam(
                    dtoSpec = dtoSpec,
                    tableTypeSpec = tableTypeSpec,
                    parameterSpec = it,
                    selectSingle = generatedFunctions.selectBySingle[it]!!,
                    selectBulk = generatedFunctions.selectByMultiple[it]!!
                )

            }

        val selectRouteFunctionSpec = generateSelectRouteFunctionSpec(dtoSpec, tableTypeSpec, selectRouteInstallSpecs)
        FileSpec.builder("$generatedPackageName.routes", "${tableClassName.simpleName}Routes")
            .addImport(dtoSpec.packageName, dtoSpec.simpleName)
            .addImport(
                "org.jetbrains.exposed.sql.transactions.experimental",
                "newSuspendedTransaction"
            )
            .addImport("$generatedPackageName.queries", generatedFunctions.insertSingle.name)
            .foldOn(generatedFunctions.selectBySingle.values) { acc, spec ->
                acc.addImport("$generatedPackageName.queries", spec.name)
            }
            .foldOn(generatedFunctions.selectByMultiple.values) { acc, spec ->
                acc.addImport("$generatedPackageName.queries", spec.name)
            }
            .addImport("io.ktor.server.application", "call")
            .addImport("io.ktor.server.auth", "authenticate")
            .addImport("io.ktor.server.request", "receive")
            .addImport("io.ktor.server.response", "respond")
            .addImport("io.ktor.server.routing", "Route", "put", "get")
            .addImport("io.ktor.http", "HttpMethod")
            .addImport("java.sql", "Connection")
            .addImport(
                RestRepositoriesRouteSetupKey::class.java.packageName,
                RestRepositoriesRouteSetupKey::class.simpleName!!
            )
            .addImport(EndpointsSetup::class.java.packageName, "RestRepositoriesConfiguration.EndpointsSetup")
            .addFunction(insertRouteInstallSpec)
            .foldOn(selectRouteInstallSpecs) { acc, spec -> acc.addFunction(spec) }
            .addFunction(selectRouteFunctionSpec)
            .addFunction(
                generateTableEndpointSetup(
                    dtoSpec = dtoSpec,
                    tableTypeSpec = tableTypeSpec,
                    insertRouteInstallSpec = insertRouteInstallSpec,
                    selectRouteInstallSpec = selectRouteFunctionSpec
                )
            )
            .build()
            .writeTo(codeGenerator, Dependencies(false, originatingFile))
    }

    private fun writeFunctions(
        generatedPackageName: String,
        dtoClassName: ClassName,
        allProperties: List<ParameterSpec>,
        tableClassName: ClassName,
        originatingFile: KSFile
    ): GeneratedFunctions {
        val functions = GeneratedFunctions(
            generateSingleInsert(dtoClassName, allProperties, tableClassName),
            generateBulkSingleInsert(dtoClassName, allProperties, tableClassName),
            allProperties.associateWith {
                generateSelectBySingleProperty(
                    dtoClassName = dtoClassName,
                    parameter = it,
                    allParameters = allProperties,
                    tableTypeSpec = tableClassName
                )
            },
            allProperties.associateWith {
                generateSelectByMultipleProperties(
                    dtoClassName = dtoClassName,
                    parameter = it,
                    allParameters = allProperties,
                    tableTypeSpec = tableClassName
                )
            },
            allProperties.associateWith { generateDeleteBySingleProperty(it, tableClassName) },
            allProperties.associateWith {
                generateUpdateBySingleProperty(
                    dtoClassName = dtoClassName,
                    parameter = it,
                    allParameters = allProperties,
                    tableTypeSpec = tableClassName
                )
            },
        )
        FileSpec.builder("$generatedPackageName.queries", "${tableClassName.simpleName}Queries")
            .addImport(
                "org.jetbrains.exposed.sql",
                "insert", "Transaction", "select", "deleteWhere", "update"
            )
            .addImport(dtoClassName.packageName, dtoClassName.simpleName)
            .addFunction(functions.insertSingle)
            .addFunction(functions.insertBulk)
            .foldOn(functions.selectBySingle.values) { acc, spec -> acc.addFunction(spec) }
            .foldOn(functions.delete.values) { acc, spec -> acc.addFunction(spec) }
            .foldOn(functions.selectByMultiple.values) { acc, spec -> acc.addFunction(spec) }
            .foldOn(functions.update.values) { acc, spec -> acc.addFunction(spec) }
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



