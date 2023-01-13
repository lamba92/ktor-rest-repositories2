package com.github.lamba92.ktor.restrepositories.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.provideDelegate
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class RestRepositoriesPlugin : Plugin<Project> {

    companion object {
        const val REST_REPOSITORIES_PROCESSOR_COORDINATES = "com.github.lamba92:ktor-rest-repositories-symbol-processor"
    }

    override fun apply(target: Project): Unit = with(target) {
        plugins.withId("com.google.devtools.ksp") {
            val ksp: Configuration by configurations
            dependencies {
                ksp("$REST_REPOSITORIES_PROCESSOR_COORDINATES:$REST_REPOSITORIES_VERSION")
            }
        }
        plugins.withId("org.jetbrains.kotlin.jvm") {
            val kotlin: KotlinJvmProjectExtension by extensions
            kotlin.apply {
                target {
                    compilations {
                        all {
                            kotlinOptions {
                                freeCompilerArgs += "-Xcontext-receivers"
                            }
                        }
                    }
                }
                sourceSets.all {
                    this.kotlin.srcDir(buildDir.resolve("generated/ksp/$name/kotlin"))
                }
            }
        }
    }

}