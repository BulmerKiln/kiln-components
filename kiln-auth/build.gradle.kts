plugins {
    id("kiln.java-library")
    `java-test-fixtures`
}

description = "Bulmer Kiln authentication subsystem: tokens, the credential ladder, and the contract every sign-in connector implements"

dependencies {
    api(project(":kiln-core"))
    api(project(":kiln-database"))
    api(project(":kiln-web"))
    // The stores take the caller's DSLContext, so jOOQ is part of this module's API.
    api("org.jooq:jooq")
    implementation("org.springframework.security:spring-security-oauth2-client")
    api("org.springframework.security:spring-security-config")
    // Public auto-configuration classes carry its annotations, so it is part of this module's API.
    api("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    compileOnly("jakarta.xml.bind:jakarta.xml.bind-api")
    compileOnly("com.github.spotbugs:spotbugs-annotations")

    // The suite every connector runs.
    testFixturesApi("org.junit.jupiter:junit-jupiter")
    testFixturesImplementation("org.assertj:assertj-core")

    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation(testFixtures(project(":kiln-database")))
    testCompileOnly("jakarta.xml.bind:jakarta.xml.bind-api")
    testCompileOnly("com.github.spotbugs:spotbugs-annotations")
}
