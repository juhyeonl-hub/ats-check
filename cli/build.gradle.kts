plugins {
    application
    id("org.graalvm.buildtools.native") version "0.10.4"
}

dependencies {
    implementation(project(":core"))
    implementation("info.picocli:picocli:4.7.6")
    implementation("org.apache.pdfbox:pdfbox:3.0.3")
    annotationProcessor("info.picocli:picocli-codegen:4.7.6")
}

application {
    applicationName = "ats-check"
    mainClass.set("dev.juhyeonl.atscheck.cli.CheckCommand")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Aproject=${project.group}/${project.name}")
}

graalvmNative {
    metadataRepository {
        enabled.set(true)
    }

    binaries {
        named("main") {
            imageName.set("ats-check")
            mainClass.set("dev.juhyeonl.atscheck.cli.CheckCommand")
            sharedLibrary.set(false)
            buildArgs.add("-H:+AddAllCharsets")
        }
    }
}
