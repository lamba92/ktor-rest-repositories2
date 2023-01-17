@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "gradle-plugin"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../dependencies.toml"))
        }
    }
    repositories {
        mavenCentral()
    }
}
