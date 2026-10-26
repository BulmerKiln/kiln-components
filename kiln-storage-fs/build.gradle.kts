plugins {
    id("kiln.java-library")
}

description = "Bulmer Kiln filesystem blob store"

dependencies {
    api(project(":kiln-storage"))
    // Public auto-configuration classes carry its annotations, so it is part of this module's API.
    api("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation(testFixtures(project(":kiln-storage")))
    testImplementation("org.springframework.boot:spring-boot-test")
}
