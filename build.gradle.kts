plugins {
    id("org.jetbrains.kotlin.multiplatform").version("1.5.0")
    id("org.gradle.maven-publish")
}

group = "com.github.exerosis.mynt"
version = "1.0.7"

repositories { mavenCentral() }

kotlin { jvm() }