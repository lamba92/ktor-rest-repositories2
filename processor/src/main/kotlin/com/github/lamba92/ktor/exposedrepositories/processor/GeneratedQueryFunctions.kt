package com.github.lamba92.ktor.exposedrepositories.processor

import com.squareup.kotlinpoet.FunSpec

data class GeneratedQueryFunctions(
    val insertSingle: FunSpec,
    val insertBulk: FunSpec,
    val selectBySingle: Map<String, FunSpec>,
    val selectByMultiple: Map<String, FunSpec>,
    val delete: Map<String, FunSpec>,
    val update: Map<String, FunSpec>
)