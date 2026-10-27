plugins {
    id("kiln.java-library")
}

description = "Bulmer Kiln OCI distribution format: images, Helm charts and OCI artifacts"

dependencies {
    api(project(":kiln-format"))
    api(project(":kiln-web"))
    api(project(":kiln-registry"))
    api(project(":kiln-database"))
    api(project(":kiln-storage"))
    api("org.jooq:jooq")
    implementation("tools.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.core:jackson-annotations")
    // Public auto-configuration classes carry its annotations, so it is part of this module's API.
    api("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    // The web server provides these at runtime; the controller only exists in a web application.
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    compileOnly("org.springframework:spring-web")
    // jOOQ's settings carry JAXB annotations; the compiler needs them to read its classes, nothing does at runtime.
    compileOnly("jakarta.xml.bind:jakarta.xml.bind-api")
    // Per-site suppressions, each with its justification.
    compileOnly("com.github.spotbugs:spotbugs-annotations")
    testCompileOnly("com.github.spotbugs:spotbugs-annotations")

    testImplementation(testFixtures(project(":kiln-format")))
    testImplementation(testFixtures(project(":kiln-database")))
    testImplementation(project(":kiln-storage-fs"))
    testImplementation("org.springframework:spring-test")
    testImplementation("org.hamcrest:hamcrest")
    testRuntimeOnly("com.jayway.jsonpath:json-path")
    testRuntimeOnly("org.skyscreamer:jsonassert")
    testImplementation("org.springframework:spring-web")
    testRuntimeOnly("org.springframework:spring-webmvc")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("jakarta.servlet:jakarta.servlet-api")
    testCompileOnly("jakarta.xml.bind:jakarta.xml.bind-api")
}
