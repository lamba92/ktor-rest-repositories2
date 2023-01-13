plugins {
    kotlin("jvm")
}

kotlin {
    target {
        compilations {
            all {
                kotlinOptions {
                    jvmTarget = "17"
                }
            }
        }
    }
}