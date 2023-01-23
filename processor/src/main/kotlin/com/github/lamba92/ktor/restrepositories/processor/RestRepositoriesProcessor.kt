package com.github.lamba92.ktor.restrepositories.processor

import com.github.lamba92.ktor.restrepositories.Endpoint
import com.github.lamba92.ktor.restrepositories.EndpointsSetup
import com.github.lamba92.ktor.restrepositories.annotations.RestRepository
import com.github.lamba92.ktor.restrepositories.processor.endpoints.*
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.writeTo
import io.ktor.http.*

class RestRepositoriesProcessor(private val codeGenerator: CodeGenerator, private val loggerContext: LoggerContext) :
    SymbolProcessor {

    companion object {
        val RestRepositoryFQN = RestRepository::class.qualifiedName!!
    }

    override fun process(resolver: Resolver): List<KSAnnotated> = with(loggerContext) {
        resolver.getDeclaredTables()
            .generateDTOSpecs()
            .forEach { dtoSpecs ->
                val originatingFile = dtoSpecs.specs.tableDeclaration.declaration.containingFile
                    ?: error("Unable to lookup file for ${dtoSpecs.specs.tableDeclaration.className.canonicalName}")
                writeDtos(dtoSpecs.specs, originatingFile)
                writeQueries(dtoSpecs, originatingFile)
                writeRoutes(dtoSpecs, originatingFile)
            }
        emptyList()
    }

    private fun writeQueries(
        dtoSpecs: DTOSpecs.WithFunctions,
        originatingFile: KSFile
    ) {
        FileSpec.builder(
            "${dtoSpecs.specs.tableDeclaration.className.packageName}.queries",
            "${dtoSpecs.specs.tableDeclaration.className.simpleName}Queries"
        )
            .addImport(
                "org.jetbrains.exposed.sql",
                "insert", "Transaction", "select", "deleteWhere", "update"
            )
            .addImport("org.jetbrains.exposed.sql.SqlExpressionBuilder", "eq")
            .addImport(dtoSpecs.specs.className.packageName, dtoSpecs.specs.className.simpleName)
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
            .foldOn(dtoSpecs.functions.delete.values) { acc, next ->
                acc.addFunction(next)
            }
            .build()
            .writeTo(codeGenerator, Dependencies(false, originatingFile))
    }

    private fun writeRoutes(dtoSpecs: DTOSpecs.WithFunctions, originatingFile: KSFile) {
        val insertRouteInstallSpec = generateInsertRouteFunctionSpec(dtoSpecs)
        val selectRouteInstallSpecs = dtoSpecs.functions
            .selectBySingle
            .keys
            .map { name ->
                generateSelectRouteFunctionSpecForParam(
                    dtoProperty = dtoSpecs.specs.properties.first { it.declarationSimpleName == name },
                    dtoSpec = dtoSpecs
                )
            }
        val updateRouteInstallSpec = dtoSpecs.functions
            .update
            .keys
            .map { name ->
                generateUpdateRouteFunctionSpecForParam(
                    dtoSpecs = dtoSpecs,
                    dtoProperty = dtoSpecs.specs.properties.first { it.declarationSimpleName == name }
                )
            }
//        val deleteRouteInstallSpec = generatedFunctions.delete
//            .map { (parameterSpec, functionSpec) ->
//                generateDeleteRouteFunctionSpecForParam(
//                    tableTypeSpec = tableTypeSpec,
//                    parameterSpec = parameterSpec,
//                    dtoSpecs = dtoSpecs,
//                    updateFunctionSpec = functionSpec
//                )
//            }
        val selectRouteFunctionSpec = generateRouteFunctionSpec(
            dtoSpecs = dtoSpecs.specs,
            functions = selectRouteInstallSpecs,
            clauseName = "select"
        )

        val updateRouteFunctionSpec = generateRouteFunctionSpec(
            dtoSpecs = dtoSpecs.specs,
            functions = updateRouteInstallSpec,
            clauseName = "update"
        )
//        val deleteRouteFunctionSpec = generateRouteFunctionSpec(
//            dtoClassName = dtoSpecs.dtoClassName,
//            tableTypeSpec = tableTypeSpec,
//            functions = deleteRouteInstallSpec,
//            clauseName = "delete"
//        )
        val optInCLassName = ClassName("kotlin", "OptIn")
        val basePackage = dtoSpecs.specs.tableDeclaration.className.packageName
        val packageName = "$basePackage.routes"
        val fileName = "${dtoSpecs.specs.tableDeclaration.className.simpleName}Routes"
        FileSpec.builder(packageName, fileName)
            .addAnnotation(
                AnnotationSpec.builder(optInCLassName)
                    .addMember("%T::class", ClassName("io.ktor.util", "InternalAPI"))
                    .build()
            )
            .addImport(dtoSpecs.specs.className.packageName, dtoSpecs.specs.className.simpleName)
            .addImport(dtoSpecs.specs.updateQueryClassName.packageName, dtoSpecs.specs.updateQueryClassName.simpleName)
            .addImport(
                "org.jetbrains.exposed.sql.transactions.experimental",
                "newSuspendedTransaction"
            )
            .addImport("$basePackage.queries", dtoSpecs.functions.insertSingle.name)
            .foldOn(dtoSpecs.functions.selectBySingle.values) { acc, spec ->
                acc.addImport("$basePackage.queries", spec.name)
            }
            .foldOn(dtoSpecs.functions.selectByMultiple.values) { acc, spec ->
                acc.addImport("$basePackage.queries", spec.name)
            }
            .foldOn(dtoSpecs.functions.update.values) { acc, spec ->
                acc.addImport("$basePackage.queries", spec.name)
            }
            .foldOn(dtoSpecs.functions.delete.values) { acc, spec ->
                acc.addImport("$basePackage.queries", spec.name)
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
//            .foldOn(deleteRouteInstallSpec) { acc, spec -> acc.addFunction(spec) }
            .addFunction(selectRouteFunctionSpec)
            .addFunction(updateRouteFunctionSpec)
//            .addFunction(deleteRouteFunctionSpec)
            .addFunction(
                generateTableEndpointSetup(
                    dtoSpecs = dtoSpecs.specs,
                    insertRouteInstallSpec = insertRouteInstallSpec,
                    selectRouteInstallSpec = selectRouteFunctionSpec,
                    updateRouteInstallSpec = updateRouteFunctionSpec,
//                    deleteRouteInstallSpec = deleteRouteFunctionSpec,
                )
            )
            .build()
            .writeTo(codeGenerator, Dependencies(false, originatingFile))
    }

    private fun writeDtos(
        dtoSpecs: DTOSpecs,
        originatingFile: KSFile
    ) {
        val packageName = dtoSpecs.tableDeclaration.className.packageName
        FileSpec.builder("$packageName.dto", dtoSpecs.className.simpleName)
            .addType(dtoSpecs.dto)
            .addType(dtoSpecs.updateQueryDto)
            .build()
            .writeTo(codeGenerator, Dependencies(false, originatingFile))
    }

}




