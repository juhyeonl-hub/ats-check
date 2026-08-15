plugins {
    application
    id("org.graalvm.buildtools.native") version "0.10.4"
}

dependencies {
    implementation(project(":core"))
    implementation("info.picocli:picocli:4.7.6")
    implementation("org.yaml:snakeyaml:2.3")
    annotationProcessor("info.picocli:picocli-codegen:4.7.6")
}

application {
    mainClass.set("dev.juhyeonl.atscheck.cli.AtsCheckCli")
}

val generatedVersionSourceDir = layout.buildDirectory.dir("generated/sources/atsCheckVersion/java")

val generateBuildInfo by tasks.registering {
    val outputFile = generatedVersionSourceDir.map {
        it.file("dev/juhyeonl/atscheck/cli/BuildInfo.java")
    }

    inputs.property("atsCheckVersion", provider { project.version.toString() })
    outputs.file(outputFile)

    doLast {
        val escapedVersion = project.version.toString()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        val buildInfoFile = outputFile.get().asFile
        buildInfoFile.parentFile.mkdirs()
        buildInfoFile.writeText(
            """
            package dev.juhyeonl.atscheck.cli;

            final class BuildInfo {
                private static final String VERSION = "$escapedVersion";

                private BuildInfo() {
                }

                static String version() {
                    return VERSION;
                }
            }
            """.trimIndent() + "\n"
        )
    }
}

sourceSets {
    main {
        java.srcDir(generatedVersionSourceDir)
    }
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn(generateBuildInfo)
    options.compilerArgs.add("-Aproject=${project.group}/${project.name}")
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("ats-check")
            mainClass.set("dev.juhyeonl.atscheck.cli.AtsCheckCli")
            sharedLibrary.set(false)
        }
    }
}
