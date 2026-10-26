plugins {
    id("kiln.java-library")
}

description = "Bulmer Kiln generic OpenID Connect sign-in connector, with presets"

dependencies {
    api(project(":kiln-auth"))
    // Public auto-configuration classes carry its annotations, so it is part of this module's API.
    api("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation(testFixtures(project(":kiln-auth")))
    testImplementation("org.springframework.boot:spring-boot-test")
}
