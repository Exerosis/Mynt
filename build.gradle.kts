plugins {
    id("org.jetbrains.kotlin.multiplatform").version("1.5.0")
    id("org.gradle.maven-publish")
}

group = "com.github.exerosis.mynt"
version = "1.0.10"

repositories { mavenCentral() }

//dependencies {
//    implem(npm("turbo-net", "1.4.0"))
//}

kotlin { jvm() }