plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

val githubRef = System.getenv("GITHUB_REF")
    ?.split("/")
    ?.lastOrNull()
    .takeIf { it != "master" }

allprojects {
    group = "com.github.lamba92"
    version = githubRef ?: "1.0-SNAPSHOT"
}
