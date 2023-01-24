plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.ksp)
    id("com.github.lamba92.ktor.exposed-repositories")
}

restRepositories {
    addDependency.set(false)
}

dependencies {
    implementation(libs.exposed.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(projects.ktorPlugin)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.call.logging)
    ksp(projects.processor)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.exposed.jdbc)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.h2)
    testRuntimeOnly(libs.logback.classic)
}

tasks.withType<Test> {
    useJUnitPlatform()
    environment("DB_FILE_PATH", file("$buildDir/testDb.db").absolutePath)
}
