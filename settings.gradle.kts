plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "BigSeekBot"

include(":llm-polling-bot")
project(":llm-polling-bot").projectDir = file("libs/llm-polling-bot")