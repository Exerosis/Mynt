plugins {
    kotlin("multiplatform") version("1.3.72")
    `maven-publish`
}

group = "com.gitlab.mynt"
version = "1.0.0"

repositories { mavenCentral() }

kotlin {
    sourceSets {
        jvm { mavenPublication {
            group = "com.gitlab.mynt"
            artifactId = "Mynt"
        } }
        js { nodejs {} }

        val commonMain by getting {
            dependencies { implementation(kotlin("stdlib-common")) }
        }
        val jvmMain by getting {
            dependencies { implementation(kotlin("stdlib-jdk8")) }
        }
        val jsMain by getting {
            dependencies {
                implementation(kotlin("stdlib-js"))
                implementation(npm("turbo-net", "1.4.0"))
            }
        }
    }
}