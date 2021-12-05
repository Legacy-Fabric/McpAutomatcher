import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

group = "net.legacyfabric"
version = "1.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.cadixdev", "lorenz", "0.5.8")

    implementation("org.ow2.asm", "asm", "9.2")
    implementation("org.ow2.asm", "asm-analysis", "9.2")
    implementation("org.ow2.asm", "asm-commons", "9.2")
    implementation("org.ow2.asm", "asm-tree", "9.2")
    implementation("org.ow2.asm", "asm-util", "9.2")
}

tasks {
    named<ShadowJar>("shadowJar") {
        mergeServiceFiles()
        manifest {
            attributes(
                mapOf("Main-Class" to "net.legacyfabric.mcpmatcher.McpAutoMatcher")
            )
        }
    }
}

tasks {
    build {
        dependsOn(shadowJar)
    }
}