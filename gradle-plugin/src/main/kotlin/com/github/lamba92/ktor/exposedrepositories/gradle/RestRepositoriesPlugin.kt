package com.github.lamba92.ktor.exposedrepositories.gradle

import org.gradle.api.Named
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

@Suppress("unused") // It's a plugin!
class RestRepositoriesPlugin : Plugin<Project> {

    open class Extension(
        private val name: String,
        val addDependency: Property<Boolean>,
        val dependencyVersion: Property<String>
    ) : Named {


        override fun getName() = name

    }

    companion object {
        const val REST_REPOSITORIES_PROCESSOR_COORDINATES = "com.github.lamba92:ktor-exposed-repositories-symbol-processor"
    }

    override fun apply(target: Project): Unit = with(target) {
        val extension = extensions.create<Extension>(
            "restRepositories",
            "restRepositories",
            objects.property<Boolean>().apply { set(true) },
            objects.property<String>().apply { set(REST_REPOSITORIES_VERSION) },
        )
        plugins.withId("com.google.devtools.ksp") {
            afterEvaluate {
                if (extension.addDependency.get()) {
                    val ksp: Configuration by configurations
                    dependencies {
                        ksp("$REST_REPOSITORIES_PROCESSOR_COORDINATES:${extension.dependencyVersion.get()}")
                    }
                }
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