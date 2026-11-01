// The convention every Kiln service applies: the kiln.java base, built into one executable Spring
// Boot jar, with the same dependency-health checks as kiln-components. Services are images, not
// Maven artifacts, so nothing is published from here.

plugins {
    id("kiln.java")
    id("kiln.dependency-health")
    id("org.springframework.boot")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName = "${project.name}.jar"
}

// The plain jar isn't shipped; only the executable one is.
tasks.named<Jar>("jar") {
    enabled = false
}

// The Jakarta annotations API is EPL-2.0 or GPL-2.0 (BUILD-16), but Tomcat needs its classes to
// start. Tomcat's own copy of the same API is Apache-2.0, so services use that one instead.
configurations.configureEach {
    exclude(group = "jakarta.annotation", module = "jakarta.annotation-api")
}
dependencies {
    "runtimeOnly"("org.apache.tomcat:tomcat-annotations-api")
}

// The Spring Boot launcher (`*Application`) only calls SpringApplication.run, and tests start the
// context without it, so it's left out of the coverage gate (BUILD-18).
tasks.withType<JacocoCoverageVerification>().configureEach {
    classDirectories.setFrom(files(classDirectories.files.map {
        fileTree(it) { exclude("**/*Application.class") }
    }))
}
tasks.withType<JacocoReport>().configureEach {
    classDirectories.setFrom(files(classDirectories.files.map {
        fileTree(it) { exclude("**/*Application.class") }
    }))
}
