package com.github.lamba92.ktor.restrepositories.processor.queries

import com.github.lamba92.ktor.restrepositories.processor.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.jetbrains.exposed.sql.Transaction

/*
params: queryParam, tableA. tableB
    val statement = tableA.select { tableA.id eq queryParam }.single()
    return DTO(
        id = statement[tableA.id]
        ciao = statement[tableA.ciao]
        referenced = selectReferencedByReferencedParam(statement[tableA.referencedParam], tableB)
    )
*/

context(LoggerContext)
fun generateSelectBySingleProperty(
    dtoSpecs: DTOSpecs,
    dtoProperty: DTOProperty,
    map: MutableMap<TableDeclaration, DTOSpecs.WithFunctions>
): FunSpec {
    val tableParameter = dtoSpecs.tableDeclaration.className.asParameter()
    val funSpec = FunSpec
        .builder("select${dtoSpecs.tableDeclaration.providedSingularName}By${dtoProperty.parameter.name.capitalize()}")
        .contextReceiver<Transaction>()
        .addParameter("parameter", dtoProperty.parameter.type.copy(nullable = false))
        .addParameter(tableParameter)
        .returns(dtoSpecs.dtoClassName)
    val tablesToAdd = mutableSetOf<ParameterSpec>()
    val codeBlock = CodeBlock.builder()
        .addStatement(
            format = "val statement = %N.select { %N.%L eq parameter }.single()",
            tableParameter, tableParameter, dtoProperty.declarationSimpleName
        )
        .addStatement("return %T(", dtoSpecs.dtoClassName)
        .foldIndexedOn(dtoSpecs.properties) { index, acc, next ->
            when (next) {
                is DTOProperty.Simple -> acc.addStatement(
                    "\t%N = statement[%N.%N]".appendIf(index != dtoSpecs.properties.lastIndex, ","),
                    next.property, tableParameter, next.property
                )

                is DTOProperty.WithReference -> {
                    val insertSpec = map.getValue(next.reference.tableDeclaration)
                        .functions.selectBySingle.getValue(next.reference.propertyName)
                    val tables = insertSpec.parameters.drop(1)
                    tablesToAdd.addAll(tables)
                    acc.addStatement("\t%N = %N(", next.property, insertSpec)
                        .addStatement(format = "\t\tparameter = statement[%N.%L],", tableParameter, next.declarationSimpleName)
                        .foldOn(tables) { acc, tableParamSpec ->
                            acc.addStatement(
                                "\t\t%N = %N".appendIf(index != tables.lastIndex, ","),
                                tableParamSpec, tableParamSpec
                            )
                        }
                        .addStatement("\t)")
                }
            }

        }
        .addStatement(")")
        .build()
    return funSpec
        .addParameters(tablesToAdd)
        .addCode(codeBlock)
        .build()
}

fun generateSelectByMultipleProperties(
    dtoClassName: ClassName,
    parameter: ParameterSpec,
    allParameters: List<ParameterSpec>,
    tableTypeSpec: ClassName
) = FunSpec.builder(
    "select${tableTypeSpec.simpleName.appendIfMissing("s")}By${
        parameter.name.capitalize().appendIfMissing("s")
    }"
)
    .contextReceivers(Transaction::class.asTypeName())
    .receiver(tableTypeSpec)
    .addParameter(
        "parameters",
        ClassName("kotlin.collections", "List")
            .parameterizedBy(parameter.type.copy(nullable = false))
    )
    .returns(TypeName.list(dtoClassName))
    .addCode(buildString {
        appendLine("// Query for a list of ${parameter.name.appendIfMissing("s")}")
        appendLine("return select { ${parameter.name} inList parameters }")
        appendLine("\t.map {")
        appendLine("\t\t${dtoClassName.simpleName}(")
        allParameters.forEachIndexed { index, param ->
            append("\t\t\tit[${param.name}]")
            if (index != allParameters.lastIndex) appendLine(",")
            else appendLine()
        }
        appendLine("\t\t)")
        appendLine("}")
    })
    .build()