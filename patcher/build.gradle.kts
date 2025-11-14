plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    application
}

group = "net.notjustanna.patcher"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Image Processing
    // Apache Batik for SVG rendering
    implementation("org.apache.xmlgraphics:batik-transcoder:1.17")
    implementation("org.apache.xmlgraphics:batik-codec:1.17")
    runtimeOnly("commons-io:commons-io:2.14.0")

    // WebP Support
    runtimeOnly("org.sejda.imageio:webp-imageio:0.1.6")

    // Archive Handling
    implementation("org.apache.commons:commons-compress:1.26.0")
    runtimeOnly("org.apache.commons:commons-lang3:3.18.0")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.ExperimentalStdlibApi")
    }
}

application {
    mainClass.set("net.notjustanna.patcher.MainKt")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "net.notjustanna.patcher.MainKt"
        attributes["Implementation-Version"] = project.version
    }
}

tasks.build {
    dependsOn("fatJar")
}

tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
}
