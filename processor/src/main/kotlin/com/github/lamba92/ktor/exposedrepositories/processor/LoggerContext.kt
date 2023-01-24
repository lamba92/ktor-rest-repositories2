package com.github.lamba92.ktor.exposedrepositories.processor

import com.google.devtools.ksp.processing.KSPLogger

interface LoggerContext {
    val logger: KSPLogger
}