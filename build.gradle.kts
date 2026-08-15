allprojects {
    group = "dev.juhyeonl"
    version = (findProperty("atsCheckVersion") as String?) ?: "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    plugins.withType<JavaPlugin>().configureEach {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }

        dependencies {
            "testImplementation"(platform("org.junit:junit-bom:5.11.3"))
            "testImplementation"("org.junit.jupiter:junit-jupiter")
            "testImplementation"("org.assertj:assertj-core:3.26.3")
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}
