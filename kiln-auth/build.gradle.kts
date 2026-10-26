plugins {
    id("kiln.java-library")
    `java-test-fixtures`
}

description = "Bulmer Kiln authentication subsystem: tokens, the credential ladder, and the contract every sign-in connector implements"

dependencies {
    // Public auto-configuration classes carry its annotations, so it is part of this module's API.
    api("org.springframework.boot:spring-boot-autoconfigure")

    // The suite every connector runs.
    testFixturesApi("org.junit.jupiter:junit-jupiter")
    testFixturesImplementation("org.assertj:assertj-core")

    testImplementation("org.springframework.boot:spring-boot-test")
}
