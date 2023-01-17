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
}

group = "com.github.lamba92"
version = "1.0-SNAPSHOT"

gradlePlugin {
    plugins {
        create("restRepositoriesPlugin") {
            id = "io.ktor.plugins.restrepositories"
            implementationClass = "com.github.lamba92.ktor.restrepositories.gradle.RestRepositoriesPlugin"
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
            FileSpec.builder("com.github.lamba92.ktor.restrepositories.gradle", "Versions")
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