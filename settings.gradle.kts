plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "BigSeekBot"

include(":llm-polling-client")
project(":llm-polling-client").projectDir = file("libs/llm-polling-client")