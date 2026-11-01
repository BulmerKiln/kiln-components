plugins {
    id("kiln.java-library")
}

description = "Bulmer Kiln envelope encryption: Tink data keys under a key-encryption key from a KeyProvider"

dependencies {
    implementation(project(":kiln-core"))
    // KeyProvider hands out Tink's Aead, so Tink is part of this module's API.
    api("com.google.crypto.tink:tink")
    // Public auto-configuration classes carry its annotations, so it is part of this module's API.
    api("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-test")
}
