plugins {
    id("kiln.java-library")
    `java-test-fixtures`
}

description = "Bulmer Kiln storage: the BlobStore contract, backend selection and per-organization routing"

dependencies {
    api(project(":kiln-core"))
    // Public auto-configuration classes carry its annotations, so it is part of this module's API.
    api("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // The contract suite every backend runs, and an in-memory store for higher layers' tests.
    testFixturesApi("org.junit.jupiter:junit-jupiter")
    testFixturesImplementation("org.assertj:assertj-core")

    testImplementation("org.springframework.boot:spring-boot-test")
}
