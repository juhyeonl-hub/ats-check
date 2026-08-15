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

tasks.withType<JavaCompile>().configureEach {
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
