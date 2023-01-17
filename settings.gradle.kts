@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "ktor-rest-repositories"

pluginManagement {
    includeBuild("gradle-plugin")
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("dependencies.toml"))
        }
    }
    repositories {
        mavenCentral()
    }
}

include(":tests", ":processor", ":ktor-plugin")
