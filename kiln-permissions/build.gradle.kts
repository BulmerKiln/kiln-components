plugins {
    id("kiln.java-library")
    `java-test-fixtures`
}

description = "Bulmer Kiln permissions subsystem: roles, teams, role bindings and access decisions"

dependencies {
    // Its ports are kiln-core's Lookup.
    api(project(":kiln-core"))
    // Public auto-configuration classes carry its annotations, so it is part of this module's API.
    api("org.springframework.boot:spring-boot-autoconfigure")
    // Optional: when the format subsystem is present, permissions answer its ArtifactAccess port.
    compileOnly(project(":kiln-format"))
    // Optional: when the database module is present, role bindings are kept there.
    compileOnly(project(":kiln-database"))
    compileOnly("jakarta.xml.bind:jakarta.xml.bind-api")

    // The authorization-matrix suite every decision engine runs.
    testFixturesApi("org.junit.jupiter:junit-jupiter")
    testFixturesImplementation("org.assertj:assertj-core")

    testImplementation(project(":kiln-format"))
    testImplementation(project(":kiln-database"))
    testImplementation(testFixtures(project(":kiln-database")))
    testCompileOnly("jakarta.xml.bind:jakarta.xml.bind-api")
    testImplementation("org.springframework.boot:spring-boot-test")
}
