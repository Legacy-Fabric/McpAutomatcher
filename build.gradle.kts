plugins {
    java
}

group = "net.legacyfabric"
version = "0.1"

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