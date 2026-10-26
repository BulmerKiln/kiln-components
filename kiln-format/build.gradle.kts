plugins {
    id("kiln.java-library")
    `java-test-fixtures`
}

description = "Bulmer Kiln format subsystem: the contract, rules and contract tests every registry format implements"

dependencies {
    api(project(":kiln-core"))
    implementation("tools.jackson.core:jackson-databind")
    // Public auto-configuration classes carry its annotations, so it is part of this module's API.
    api("org.springframework.boot:spring-boot-autoconfigure")

    // The suite every format module runs.
    testFixturesApi("org.junit.jupiter:junit-jupiter")
    testFixturesImplementation("org.assertj:assertj-core")
    testFixturesImplementation("tools.jackson.core:jackson-databind")

    testImplementation("org.springframework.boot:spring-boot-test")
}
