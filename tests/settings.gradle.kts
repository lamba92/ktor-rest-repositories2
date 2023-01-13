@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "tests"

pluginManagement {
    includeBuild("../gradle-plugin")
    plugins {
        val kotlinVersion = "1.8.0"
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        id("com.google.devtools.ksp") version "1.8.0-1.0.8"
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

includeBuild("../") {
    dependencySubstitution {
        substitute(module("com.github.lamba92:ktor-rest-repositories-annotations")).using(project(":annotations"))
        substitute(module("com.github.lamba92:ktor-rest-repositories-symbol-processor")).using(project(":processor"))
    }
}
