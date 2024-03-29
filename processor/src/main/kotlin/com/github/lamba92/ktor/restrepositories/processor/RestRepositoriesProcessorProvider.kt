package com.github.lamba92.ktor.restrepositories.processor

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class RestRepositoriesProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment) =
        RestRepositoriesProcessor(environment.codeGenerator, environment.logger)
}