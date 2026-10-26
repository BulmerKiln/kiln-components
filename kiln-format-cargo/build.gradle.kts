plugins {
    id("kiln.java-library")
}

description = "Bulmer Kiln Cargo registry format (sparse index)"

dependencies {
    api(project(":kiln-format"))

    // Public auto-configuration classes carry its annotations, so it is part of this module's API.
    api("org.springframework.boot:spring-boot-autoconfigure")

    testImplementation(testFixtures(project(":kiln-format")))
}
