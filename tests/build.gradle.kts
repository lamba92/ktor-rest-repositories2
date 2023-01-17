plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.ksp)
    id("io.ktor.plugins.restrepositories")
}

restRepositories {
    addDependency.set(false)
}

dependencies {
    implementation(libs.exposed.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(projects.ktorPlugin)
    ksp(projects.processor)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly(libs.junit.jupiter.engine)
}
