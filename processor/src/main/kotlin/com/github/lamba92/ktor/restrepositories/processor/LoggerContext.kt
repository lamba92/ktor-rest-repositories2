package com.github.lamba92.ktor.restrepositories.processor

import com.google.devtools.ksp.processing.KSPLogger

interface LoggerContext {
    val logger: KSPLogger
}