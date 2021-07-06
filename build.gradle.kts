plugins {
    kotlin("multiplatform").version("1.5.0")
    `maven-publish`
}

group = "com.gitlab.mynt"
version = "1.0.5"

repositories { mavenCentral() }

kotlin {
    sourceSets {
//        js { nodejs {} }
        jvm { publishing { } }

        val commonMain by getting {
        }
        val jvmMain by getting {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.0")
            }
        }
//        val jsMain by getting {
//            dependencies {
//                implementation(kotlin("stdlib-js"))
//                implementation(npm("turbo-net", "1.4.0"))
//            }
//        }
    }
}