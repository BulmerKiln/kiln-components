plugins {
    id("kiln.java-library")
    `java-test-fixtures`
}

description = "Bulmer Kiln permissions subsystem: roles, teams, role bindings and access decisions"

dependencies {
    // Public auto-configuration classes carry its annotations, so it is part of this module's API.
    api("org.springframework.boot:spring-boot-autoconfigure")
    // Optional: when the format subsystem is present, permissions answer its ArtifactAccess port.
    compileOnly(project(":kiln-format"))

    // The authorization-matrix suite every decision engine runs.
    testFixturesApi("org.junit.jupiter:junit-jupiter")
    testFixturesImplementation("org.assertj:assertj-core")

    testImplementation(project(":kiln-format"))
    testImplementation("org.springframework.boot:spring-boot-test")
}
