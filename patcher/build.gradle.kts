plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    application
}

group = "net.notjustanna.patcher"
version = "2.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // Image Processing
    // Apache Batik for SVG rendering
    implementation("org.apache.xmlgraphics:batik-transcoder:1.19")
    implementation("org.apache.xmlgraphics:batik-codec:1.19")
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

// Generate BuildConfig.kt with the patcher version so runtime code can stamp
// it into sha.json for cache-invalidation purposes.
val generatedBuildConfigDir = layout.buildDirectory.dir("generated/source/buildconfig/main/kotlin")

val generateBuildConfig by tasks.registering {
    val outputDir = generatedBuildConfigDir
    val versionValue = project.version.toString()
    inputs.property("version", versionValue)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("net/notjustanna/patcher/BuildConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package net.notjustanna.patcher

            object BuildConfig {
                const val VERSION: String = "$versionValue"
            }
            """.trimIndent() + "\n"
        )
    }
}

sourceSets.main {
    kotlin.srcDir(generatedBuildConfigDir)
}

tasks.named("compileKotlin") {
    dependsOn(generateBuildConfig)
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
