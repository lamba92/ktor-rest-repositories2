package com.github.lamba92.ktor.restrepositories.processor

import com.github.lamba92.ktor.restrepositories.Endpoint
import com.github.lamba92.ktor.restrepositories.EndpointsSetup
import com.github.lamba92.ktor.restrepositories.annotations.RestRepository
import com.github.lamba92.ktor.restrepositories.processor.endpoints.*
import com.github.lamba92.ktor.restrepositories.processor.queries.*
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import io.ktor.http.*

class RestRepositoriesProcessor(private val codeGenerator: CodeGenerator, ) : SymbolProcessor {

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
            val updateQueryDtoClassName = ClassName(
                packageName = "$generatedPackageName.dto",
                "${tableClassDeclaration.simpleName.asString().removeSuffix("s")}UpdateQueryDTO"
            )
            val dtoSpecs = generateDtos(dtoClassName, updateQueryDtoClassName, dtoPropertiesSpecs)
            val tableTypeSpec = tableClassDeclaration.toClassName()
            writeDtos(
                generatedPackageName = generatedPackageName,
                dtoClassName = dtoClassName,
                dtoSpecs = dtoSpecs,
                originatingFile = originatingFile
            )
            val functions = writeQueries(
                generatedPackageName = generatedPackageName,
                dtoClassName = dtoClassName,
                dtoPropertiesSpecs = dtoPropertiesSpecs,
                tableClassName = tableTypeSpec,
                originatingFile = originatingFile
            )
            writeRoutes(
                tableTypeSpec = tableTypeSpec,
                generatedFunctions = functions,
                generatedPackageName = generatedPackageName,
                tableClassName = tableTypeSpec,
                originatingFile = originatingFile,
                dtoSpecs = dtoSpecs
            )

        }

        return emptyList()
    }

    private fun writeRoutes(
        tableTypeSpec: ClassName,
        generatedFunctions: GeneratedQueryFunctions,
        generatedPackageName: String,
        tableClassName: ClassName,
        originatingFile: KSFile,
        dtoSpecs: DTOSpecs
    ) {
        val insertRouteInstallSpec = generateInsertRouteFunctionSpec(
            dtoSpec = dtoSpecs.dtoClassName,
            tableTypeSpec = tableTypeSpec,
            insertSingle = generatedFunctions.insertSingle,
            insertBulk = generatedFunctions.insertBulk
        )
        val selectRouteInstallSpecs = generatedFunctions.selectBySingle.keys
            .map {
                generateSelectRouteFunctionSpecForParam(
                    dtoClassName = dtoSpecs.dtoClassName,
                    tableTypeSpec = tableTypeSpec,
                    parameterSpec = it,
                    selectSingle = generatedFunctions.selectBySingle[it]!!,
                    selectBulk = generatedFunctions.selectByMultiple[it]!!
                )

            }
        val updateRouteInstallSpec = generatedFunctions.update
            .map { (parameterSpec, functionSpec) ->
                generateUpdateRouteFunctionSpecForParam(
                    tableTypeSpec = tableTypeSpec,
                    parameterSpec = parameterSpec,
                    dtoSpecs = dtoSpecs,
                    updateFunctionSpec = functionSpec
                )
            }
        val deleteRouteInstallSpec = generatedFunctions.delete
            .map { (parameterSpec, functionSpec) ->
                generateDeleteRouteFunctionSpecForParam(
                    tableTypeSpec = tableTypeSpec,
                    parameterSpec = parameterSpec,
                    dtoSpecs = dtoSpecs,
                    updateFunctionSpec = functionSpec
                )
            }
        val selectRouteFunctionSpec = generateRouteFunctionSpec(
            dtoClassName = dtoSpecs.dtoClassName,
            tableTypeSpec = tableTypeSpec,
            functions = selectRouteInstallSpecs,
            clauseName = "select"
        )

        val updateRouteFunctionSpec = generateRouteFunctionSpec(
            dtoClassName = dtoSpecs.dtoClassName,
            tableTypeSpec = tableTypeSpec,
            functions = updateRouteInstallSpec,
            clauseName = "update"
        )
        val deleteRouteFunctionSpec = generateRouteFunctionSpec(
            dtoClassName = dtoSpecs.dtoClassName,
            tableTypeSpec = tableTypeSpec,
            functions = deleteRouteInstallSpec,
            clauseName = "delete"
        )
        val optInCLassName = ClassName("kotlin", "OptIn")
        FileSpec.builder("$generatedPackageName.routes", "${tableClassName.simpleName}Routes")
            .addAnnotation(
                AnnotationSpec.builder(optInCLassName)
                    .addMember("%T::class", ClassName("io.ktor.util", "InternalAPI"))
                    .build()
            )
            .addImport(dtoSpecs.dtoClassName.packageName, dtoSpecs.dtoClassName.simpleName)
            .addImport(dtoSpecs.updateQueryDtoClassName.packageName, dtoSpecs.updateQueryDtoClassName.simpleName)
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
            .foldOn(generatedFunctions.update.values) { acc, spec ->
                acc.addImport("$generatedPackageName.queries", spec.name)
            }
            .foldOn(generatedFunctions.delete.values) { acc, spec ->
                acc.addImport("$generatedPackageName.queries", spec.name)
            }
            .addImport("io.ktor.server.application", "call")
            .addImport("io.ktor.server.auth", "authenticate")
            .addImport("io.ktor.server.request", "receive")
            .addImport("io.ktor.server.response", "respond")
            .addImport("io.ktor.http", HttpStatusCode::class.simpleName!!)
            .addImport("io.ktor.server.routing", "Route", "put", "get", "post", "delete")
            .addImport("java.sql", "Connection")
            .addImport(EndpointsSetup::class.java.packageName, EndpointsSetup::class.simpleName!!)
            .addImport(Endpoint::class.java.packageName, Endpoint::class.simpleName!!)
            .addImport(EndpointsSetup::class.java.packageName, EndpointsSetup::class.simpleName!!)
            .addFunction(insertRouteInstallSpec)
            .foldOn(selectRouteInstallSpecs) { acc, spec -> acc.addFunction(spec) }
            .foldOn(updateRouteInstallSpec) { acc, spec -> acc.addFunction(spec) }
            .foldOn(deleteRouteInstallSpec) { acc, spec -> acc.addFunction(spec) }
            .addFunction(selectRouteFunctionSpec)
            .addFunction(updateRouteFunctionSpec)
            .addFunction(deleteRouteFunctionSpec)
            .addFunction(
                generateTableEndpointSetup(
                    dtoClassName = dtoSpecs.dtoClassName,
                    tableTypeSpec = tableTypeSpec,
                    insertRouteInstallSpec = insertRouteInstallSpec,
                    selectRouteInstallSpec = selectRouteFunctionSpec,
                    updateRouteInstallSpec = updateRouteFunctionSpec,
                    deleteRouteInstallSpec = deleteRouteFunctionSpec,
                )
            )
            .build()
            .writeTo(codeGenerator, Dependencies(false, originatingFile))
    }

    private fun writeQueries(
        generatedPackageName: String,
        dtoClassName: ClassName,
        dtoPropertiesSpecs: List<DTOPropertiesSpec>,
        tableClassName: ClassName,
        originatingFile: KSFile
    ): GeneratedQueryFunctions {
        val allProperties = dtoPropertiesSpecs.map { it.parameter }
        val functions = GeneratedQueryFunctions(
            generateSingleInsert(dtoClassName, allProperties, tableClassName),
            generateBulkInsert(dtoClassName, allProperties, tableClassName),
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
            dtoPropertiesSpecs.associate {
                it.parameter to generateUpdateBySingleProperty(
                    dtoClassName = dtoClassName,
                    dtoParameter = it.parameter,
                    dtoPropertiesSpecs = dtoPropertiesSpecs,
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

    private fun writeDtos(
        generatedPackageName: String,
        dtoClassName: ClassName,
        dtoSpecs: DTOSpecs,
        originatingFile: KSFile
    ) = FileSpec.builder("$generatedPackageName.dto", dtoClassName.simpleName)
        .addType(dtoSpecs.dto)
        .addType(dtoSpecs.updateQueryDto)
        .build()
        .writeTo(codeGenerator, Dependencies(false, originatingFile))

}




