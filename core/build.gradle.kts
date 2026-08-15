plugins {
    `java-library`
    jacoco
}

dependencies {
    testImplementation("org.yaml:snakeyaml:2.3")
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

// Runtime dependencies intentionally stay empty.
