plugins {
    kotlin("plugin.serialization")
    kotlin("jvm")
    id("com.google.devtools.ksp")
    id("io.ktor.plugins.restrepositories")
}

restRepositories {
    addDependency.set(false)
}

dependencies {
    val jupyterVersion = "5.9.2"

    implementation("org.jetbrains.exposed:exposed:0.17.14")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.4.1")
    implementation(projects.annotations)
    ksp(projects.processor)
    testImplementation("org.junit.jupiter:junit-jupiter-api:$jupyterVersion")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$jupyterVersion")
}

