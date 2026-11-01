plugins {
    id("kiln.java-library")
    `java-test-fixtures`
}

description = "Bulmer Kiln database: Flyway, row-level security, the organization per transaction, and the schema linter"

dependencies {
    api(project(":kiln-core"))
    api("org.jooq:jooq")
    // Repositories in every module extend KilnRepository, and services run through the transaction
    // manager this module provides.
    api("org.springframework.data:spring-data-jdbc")
    api("org.springframework:spring-tx")
    implementation("org.springframework:spring-jdbc")
    // Public auto-configuration classes carry its annotations, so it is part of this module's API.
    api("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // jOOQ's settings carry JAXB annotations; the compiler needs them to read its classes, nothing does at runtime.
    compileOnly("jakarta.xml.bind:jakarta.xml.bind-api")
    testCompileOnly("jakarta.xml.bind:jakarta.xml.bind-api")
    implementation("com.zaxxer:HikariCP")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testFixturesApi(project(":kiln-core"))
    // Repositories for tests that build services by hand.
    testFixturesImplementation("org.springframework:spring-jdbc")
    testFixturesImplementation("org.testcontainers:testcontainers-postgresql")
    testFixturesRuntimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-test")
}
