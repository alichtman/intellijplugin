group = "edu.illinois.cs.cs125"
version = "1.0-SNAPSHOT"

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(kotlin("gradle-plugin", "1.2.50"))
    }
}

plugins {
    id("org.jetbrains.intellij") version "0.3.4"
    kotlin("jvm") version "1.2.50"
}

repositories {
    jcenter()
}
dependencies {
    compile("org.yaml","snakeyaml", "1.18")
    compile("org.apache.httpcomponents", "httpclient", "4.5.3")
}