plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(libs.exposed.core)
    api(libs.ktor.server.core)
    api(libs.ktor.server.auth)
}


