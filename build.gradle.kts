plugins {
    id("org.jetbrains.kotlin.multiplatform").version("1.8.0")
    id("org.gradle.maven-publish")
}

group = "com.github.exerosis.mynt"
version = "1.0.12"

repositories { mavenCentral() }

//dependencies {
//    implements(npm("turbo-net", "1.4.0"))
//}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0")
            }
        }
    }
    jvm()
}