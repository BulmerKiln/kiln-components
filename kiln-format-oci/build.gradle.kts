plugins {
    id("kiln.java-library")
}

description = "Bulmer Kiln OCI distribution format: images, Helm charts and OCI artifacts"

dependencies {
    api(project(":kiln-format"))
    implementation("tools.jackson.core:jackson-databind")
    // Public auto-configuration classes carry its annotations, so it is part of this module's API.
    api("org.springframework.boot:spring-boot-autoconfigure")

    testImplementation(testFixtures(project(":kiln-format")))
}
