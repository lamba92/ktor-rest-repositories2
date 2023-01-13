plugins {
    kotlin("plugin.serialization")
    kotlin("jvm")
    id("com.google.devtools.ksp")
    id("io.ktor.plugins.restrepositories")
}

dependencies {
    val jupyterVersion = "5.9.2"

    implementation("org.jetbrains.exposed:exposed:0.17.14")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.4.1")
    implementation("com.github.lamba92:ktor-rest-repositories-annotations:1.0-SNAPSHOT")
    ksp("com.github.lamba92:ktor-rest-repositories-symbol-processor:1.0-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$jupyterVersion")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$jupyterVersion")
}

