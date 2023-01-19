package com.github.lamba92.ktor.restrepositories.processor

import com.github.lamba92.ktor.restrepositories.annotations.RestRepository
import com.github.lamba92.ktor.restrepositories.processor.endpoints.*
import com.github.lamba92.ktor.restrepositories.processor.queries.*
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ksp.writeTo
import io.ktor.http.*

interface LoggerContext {
    val logger: KSPLogger
}

class RestRepositoriesProcessor(private val codeGenerator: CodeGenerator, private val loggerContext: LoggerContext) :
    SymbolProcessor {

    companion object {
        val RestRepositoryFQN = RestRepository::class.qualifiedName!!
    }

    override fun process(resolver: Resolver): List<KSAnnotated> = with(loggerContext) {
        resolver.getDeclaredTables()
            .generateDTOSpecs()
            .forEach { (tableDeclaration, dtoSpecs) ->
                val originatingFile = tableDeclaration.declaration.containingFile
                    ?: error("Unable to lookup file for ${tableDeclaration.className.canonicalName}")
                writeDtos(
                    rootPackageName = tableDeclaration.className.packageName,
                    dtoSpecs = dtoSpecs.specs,
                    originatingFile = originatingFile
                )
                FileSpec.builder(
                    "${tableDeclaration.className.packageName}.queries",
                    "${tableDeclaration.className.simpleName}Queries"
                )
                    .addImport(
                        "org.jetbrains.exposed.sql",
                        "insert", "Transaction", "select", "deleteWhere", "update"
                    )
                    .addImport("org.jetbrains.exposed.sql.SqlExpressionBuilder", "eq")
                    .addImport(dtoSpecs.specs.dtoClassName.packageName, dtoSpecs.specs.dtoClassName.simpleName)
                    .addFunction(dtoSpecs.functions.insertSingle)
                    .addFunction(dtoSpecs.functions.insertBulk)
                    .foldOn(dtoSpecs.functions.selectBySingle.values) { acc, next ->
                        acc.addFunction(next)
                    }
                    .foldOn(dtoSpecs.functions.selectByMultiple.values) { acc, next ->
                        acc.addFunction(next)
                    }
                    .foldOn(dtoSpecs.functions.update.values) { acc, next ->
                        acc.addFunction(next)
                    }
                    .build()
                    .writeTo(codeGenerator, Dependencies(false, originatingFile))
//                val functions = writeQueries(
//                    tableDeclaration = tableDeclaration,
//                    dtoSpecs = dtoSpecs,
//                    originatingFile = originatingFile
//                )
//                writeRoutes(
//                    tableTypeSpec = tableTypeSpec,
//                    generatedFunctions = functions,
//                    generatedPackageName = generatedPackageName,
//                    tableClassName = tableTypeSpec,
//                    originatingFile = originatingFile,
//                    dtoSpecs = dtoSpecs
//                )
            }
        emptyList()
    }

//    private fun writeRoutes(
//        tableTypeSpec: ClassName,
//        generatedFunctions: GeneratedQueryFunctions,
//        generatedPackageName: String,
//        tableClassName: ClassName,
//        originatingFile: KSFile,
//        dtoSpecs: DTOSpecs
//    ) {
//        val insertRouteInstallSpec = generateInsertRouteFunctionSpec(
//            dtoSpec = dtoSpecs.dtoClassName,
//            tableTypeSpec = tableTypeSpec,
//            insertSingle = generatedFunctions.insertSingle,
//            insertBulk = generatedFunctions.insertBulk
//        )
//        val selectRouteInstallSpecs = generatedFunctions.selectBySingle.keys
//            .map {
//                generateSelectRouteFunctionSpecForParam(
//                    dtoClassName = dtoSpecs.dtoClassName,
//                    tableTypeSpec = tableTypeSpec,
//                    parameterSpec = it,
//                    selectSingle = generatedFunctions.selectBySingle[it]!!,
//                    selectBulk = generatedFunctions.selectByMultiple[it]!!
//                )
//
//            }
//        val updateRouteInstallSpec = generatedFunctions.update
//            .map { (parameterSpec, functionSpec) ->
//                generateUpdateRouteFunctionSpecForParam(
//                    tableTypeSpec = tableTypeSpec,
//                    parameterSpec = parameterSpec,
//                    dtoSpecs = dtoSpecs,
//                    updateFunctionSpec = functionSpec
//                )
//            }
//        val deleteRouteInstallSpec = generatedFunctions.delete
//            .map { (parameterSpec, functionSpec) ->
//                generateDeleteRouteFunctionSpecForParam(
//                    tableTypeSpec = tableTypeSpec,
//                    parameterSpec = parameterSpec,
//                    dtoSpecs = dtoSpecs,
//                    updateFunctionSpec = functionSpec
//                )
//            }
//        val selectRouteFunctionSpec = generateRouteFunctionSpec(
//            dtoClassName = dtoSpecs.dtoClassName,
//            tableTypeSpec = tableTypeSpec,
//            functions = selectRouteInstallSpecs,
//            clauseName = "select"
//        )
//
//        val updateRouteFunctionSpec = generateRouteFunctionSpec(
//            dtoClassName = dtoSpecs.dtoClassName,
//            tableTypeSpec = tableTypeSpec,
//            functions = updateRouteInstallSpec,
//            clauseName = "update"
//        )
//        val deleteRouteFunctionSpec = generateRouteFunctionSpec(
//            dtoClassName = dtoSpecs.dtoClassName,
//            tableTypeSpec = tableTypeSpec,
//            functions = deleteRouteInstallSpec,
//            clauseName = "delete"
//        )
//        val optInCLassName = ClassName("kotlin", "OptIn")
//        FileSpec.builder("$generatedPackageName.routes", "${tableClassName.simpleName}Routes")
//            .addAnnotation(
//                AnnotationSpec.builder(optInCLassName)
//                    .addMember("%T::class", ClassName("io.ktor.util", "InternalAPI"))
//                    .build()
//            )
//            .addImport(dtoSpecs.dtoClassName.packageName, dtoSpecs.dtoClassName.simpleName)
//            .addImport(dtoSpecs.updateQueryDtoClassName.packageName, dtoSpecs.updateQueryDtoClassName.simpleName)
//            .addImport(
//                "org.jetbrains.exposed.sql.transactions.experimental",
//                "newSuspendedTransaction"
//            )
//            .addImport("$generatedPackageName.queries", generatedFunctions.insertSingle.name)
//            .foldOn(generatedFunctions.selectBySingle.values) { acc, spec ->
//                acc.addImport("$generatedPackageName.queries", spec.name)
//            }
//            .foldOn(generatedFunctions.selectByMultiple.values) { acc, spec ->
//                acc.addImport("$generatedPackageName.queries", spec.name)
//            }
//            .foldOn(generatedFunctions.update.values) { acc, spec ->
//                acc.addImport("$generatedPackageName.queries", spec.name)
//            }
//            .foldOn(generatedFunctions.delete.values) { acc, spec ->
//                acc.addImport("$generatedPackageName.queries", spec.name)
//            }
//            .addImport("io.ktor.server.application", "call")
//            .addImport("io.ktor.server.auth", "authenticate")
//            .addImport("io.ktor.server.request", "receive")
//            .addImport("io.ktor.server.response", "respond")
//            .addImport("io.ktor.http", HttpStatusCode::class.simpleName!!)
//            .addImport("io.ktor.server.routing", "Route", "put", "get", "post", "delete")
//            .addImport("java.sql", "Connection")
//            .addImport(EndpointsSetup::class.java.packageName, EndpointsSetup::class.simpleName!!)
//            .addImport(Endpoint::class.java.packageName, Endpoint::class.simpleName!!)
//            .addImport(EndpointsSetup::class.java.packageName, EndpointsSetup::class.simpleName!!)
//            .addFunction(insertRouteInstallSpec)
//            .foldOn(selectRouteInstallSpecs) { acc, spec -> acc.addFunction(spec) }
//            .foldOn(updateRouteInstallSpec) { acc, spec -> acc.addFunction(spec) }
//            .foldOn(deleteRouteInstallSpec) { acc, spec -> acc.addFunction(spec) }
//            .addFunction(selectRouteFunctionSpec)
//            .addFunction(updateRouteFunctionSpec)
//            .addFunction(deleteRouteFunctionSpec)
//            .addFunction(
//                generateTableEndpointSetup(
//                    dtoClassName = dtoSpecs.dtoClassName,
//                    tableTypeSpec = tableTypeSpec,
//                    insertRouteInstallSpec = insertRouteInstallSpec,
//                    selectRouteInstallSpec = selectRouteFunctionSpec,
//                    updateRouteInstallSpec = updateRouteFunctionSpec,
//                    deleteRouteInstallSpec = deleteRouteFunctionSpec,
//                )
//            )
//            .build()
//            .writeTo(codeGenerator, Dependencies(false, originatingFile))
//    }
//
//    private fun writeQueries(
//        tableDeclaration: TableDeclaration,
//        dtoSpecs: DTOSpecs,
//        originatingFile: KSFile
//    ): GeneratedQueryFunctions {
//        val functions = GeneratedQueryFunctions(
//            generateSingleInsert(tableDeclaration, dtoSpecs, map),
//            generateBulkInsert(dtoClassName, allProperties, tableClassName),
//            allProperties.associateWith {
//                generateSelectBySingleProperty(
//                    dtoClassName = dtoClassName,
//                    parameter = it,
//                    allParameters = allProperties,
//                    tableTypeSpec = tableClassName
//                )
//            },
//            allProperties.associateWith {
//                generateSelectByMultipleProperties(
//                    dtoClassName = dtoClassName,
//                    parameter = it,
//                    allParameters = allProperties,
//                    tableTypeSpec = tableClassName
//                )
//            },
//            allProperties.associateWith { generateDeleteBySingleProperty(it, tableClassName) },
//            dtoPropertiesSpecs.associate {
//                it.parameter to generateUpdateBySingleProperty(
//                    dtoClassName = dtoClassName,
//                    dtoParameter = it.parameter,
//                    dtoPropertiesSpecs = dtoPropertiesSpecs,
//                    tableTypeSpec = tableClassName
//                )
//            },
//        )
//        FileSpec.builder("$generatedPackageName.queries", "${tableClassName.simpleName}Queries")
//            .addImport(
//                "org.jetbrains.exposed.sql",
//                "insert", "Transaction", "select", "deleteWhere", "update"
//            )
//            .addImport("org.jetbrains.exposed.sql.SqlExpressionBuilder", "eq")
//            .addImport(dtoClassName.packageName, dtoClassName.simpleName)
//            .addFunction(functions.insertSingle)
//            .addFunction(functions.insertBulk)
//            .foldOn(functions.selectBySingle.values) { acc, spec -> acc.addFunction(spec) }
//            .foldOn(functions.delete.values) { acc, spec -> acc.addFunction(spec) }
//            .foldOn(functions.selectByMultiple.values) { acc, spec -> acc.addFunction(spec) }
//            .foldOn(functions.update.values) { acc, spec -> acc.addFunction(spec) }
//            .build()
//            .writeTo(codeGenerator, Dependencies(false, originatingFile))
//        return functions
//    }

    private fun writeDtos(
        rootPackageName: String,
        dtoSpecs: DTOSpecs,
        originatingFile: KSFile
    ) = FileSpec.builder("$rootPackageName.dto", dtoSpecs.dtoClassName.simpleName)
        .addType(dtoSpecs.dto)
        .addType(dtoSpecs.updateQueryDto)
        .build()
        .writeTo(codeGenerator, Dependencies(false, originatingFile))

}




