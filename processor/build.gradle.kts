plugins {
    kotlin("jvm")
}

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:1.8.0-1.0.8")
    val kotlinPoetVersion = "1.12.0"
    api("com.squareup:kotlinpoet:$kotlinPoetVersion")
    api("com.squareup:kotlinpoet-ksp:$kotlinPoetVersion")
    api(projects.annotations)
    api("org.jetbrains.exposed:exposed:0.17.14")
    api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.4.1")
}

kotlin {
    target {
        compilations {
            all {
                kotlinOptions {
                    freeCompilerArgs += "-Xcontext-receivers"
                    jvmTarget = "17"
                }
            }
        }
    }
    sourceSets {
        all {
            languageSettings {
                optIn("com.google.devtools.ksp.KspExperimental")
                optIn("com.squareup.kotlinpoet.ExperimentalKotlinPoetApi")
            }
        }
    }
}