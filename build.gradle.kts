plugins {
    kotlin("jvm") version "2.1.10"
}

group = "dev.flsrg"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.telegram:telegrambots:6.9.7.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}