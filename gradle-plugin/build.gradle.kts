@file:Suppress("UnstableApiUsage")

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.PropertySpec

buildscript {
    dependencies {
        classpath(libs.kotlinpoet)
    }
}

plugins {
    `kotlin-dsl`
    alias(libs.plugins.gradle.plugin.publish)
    signing
}

java {
    toolchain {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

group = "com.github.lamba92"
version = System.getenv("GITHUB_REF")
    ?.split("/")
    ?.lastOrNull()
    .takeIf { it != "master" }
    ?: "1.0-SNAPSHOT"

gradlePlugin {
    plugins {
        create("exposedRepositoriesPlugin") {
            id = "com.github.lamba92.ktor.exposed-repositories"
            implementationClass = "com.github.lamba92.ktor.exposedrepositories.gradle.RestRepositoriesPlugin"
            displayName = "Exposed Repositories for Ktor"
            description = "Plugin to generate code for serving Exposed tables using Ktor"
            vcsUrl.set("https://github.com/lamba92/ktor-rest-repositories.git")
            website.set("https://github.com/lamba92/ktor-rest-repositories")
        }
    }
}

dependencies {
    implementation(kotlin("gradle-plugin"))
}

val generatedOutputDir = buildDir.resolve("generated/kotlin/main")

kotlin.sourceSets.main {
    kotlin.srcDir(generatedOutputDir)
}

tasks {
    val generateVersions by registering {
        outputs.dir(generatedOutputDir)
        doFirst { generatedOutputDir.deleteRecursively() }
        doLast {
            generatedOutputDir.mkdirs()
            FileSpec.builder("com.github.lamba92.ktor.exposedrepositories.gradle", "Versions")
                .addProperty(
                    PropertySpec.builder("REST_REPOSITORIES_VERSION", String::class)
                        .getter(
                            FunSpec.getterBuilder()
                                .addCode(CodeBlock.of("return \"${project.version}\""))
                                .build()
                        )
                        .build()
                )
                .build()
                .writeTo(generatedOutputDir)
        }
    }
    compileKotlin {
        dependsOn(generateVersions)
    }
}