package com.github.lamba92.ktor.exposedrepositories.processor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class RestRepositoriesProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment) =
        RestRepositoriesProcessor(environment.codeGenerator, environment.logger.asContext())
}

fun KSPLogger.asContext() = object : LoggerContext {
    override val logger: KSPLogger
        get() = this@asContext
}
