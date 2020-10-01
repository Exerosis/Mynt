plugins {
    kotlin("multiplatform") version("1.4.0")
    `maven-publish`
}

group = "com.gitlab.mynt"
version = "1.0.0"

repositories { mavenCentral() }

kotlin {
    sourceSets {
        js { nodejs {} }
        jvm { publishing {  } }

        val commonMain by getting {
        }
        val jvmMain by getting {
        }
        val jsMain by getting {
            dependencies {
                implementation(kotlin("stdlib-js"))
                implementation(npm("turbo-net", "1.4.0"))
            }
        }
    }
}