plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.ksp.symbol.processing.api)
    api(libs.kotlinpoet)
    api(libs.kotlinpoet.ksp)
    api(projects.ktorPlugin)
    api(libs.exposed.core)
}

kotlin {
    target.compilations.all {
        kotlinOptions {
            freeCompilerArgs += "-Xcontext-receivers"
            jvmTarget = "17"
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
