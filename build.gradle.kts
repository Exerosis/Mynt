plugins {
    id("org.jetbrains.kotlin.multiplatform").version("1.5.0")
    id("org.gradle.maven-publish")
}

group = "com.github.exerosis.mynt"
version = "1.0.8"

repositories { mavenCentral() }

kotlin { jvm() }