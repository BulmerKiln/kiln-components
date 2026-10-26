plugins {
    id("kiln.java-library")
}

description = "Bulmer Kiln metrics, plus the Prometheus and Alertmanager adapters"

dependencies {
    api(project(":kiln-core"))
    api("io.micrometer:micrometer-core")
    // Public auto-configuration classes carry its annotations, so it is part of this module's API.
    api("org.springframework.boot:spring-boot-autoconfigure")
    // Optional: when the format subsystem is present, metrics listen to its push and pull events.
    compileOnly(project(":kiln-format"))
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation(project(":kiln-format"))
    testImplementation("io.micrometer:micrometer-registry-prometheus")
    testImplementation("org.springframework.boot:spring-boot-test")
}
