plugins {
    kotlin("jvm")
    `maven-publish`
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

publishing {
    repositories {
        mavenLocal()
    }
    publications {
        create<MavenPublication>(project.name) {
            from(components["java"])
        }
    }
}