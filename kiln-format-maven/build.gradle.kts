plugins {
    id("kiln.java-library")
}

description = "Bulmer Kiln Maven and Gradle repository format"

dependencies {
    api(project(":kiln-format"))
    api(project(":kiln-web"))
    api(project(":kiln-registry"))
    api(project(":kiln-database"))
    api(project(":kiln-storage"))
    implementation("org.jooq:jooq")

    // Public auto-configuration classes carry its annotations, so it is part of this module's API.
    api("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    // The web server provides these at runtime; the controller only exists in a web application.
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    compileOnly("org.springframework:spring-web")
    // jOOQ's settings carry JAXB annotations; the compiler needs them to read its classes, nothing does at runtime.
    compileOnly("jakarta.xml.bind:jakarta.xml.bind-api")

    testImplementation(testFixtures(project(":kiln-format")))
    testImplementation(testFixtures(project(":kiln-database")))
    testImplementation(project(":kiln-storage-fs"))
    testImplementation(project(":kiln-permissions"))
    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework:spring-web")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.hamcrest:hamcrest")
    testImplementation("jakarta.servlet:jakarta.servlet-api")
    testRuntimeOnly("org.springframework:spring-webmvc")
    testRuntimeOnly("com.jayway.jsonpath:json-path")
    testRuntimeOnly("org.skyscreamer:jsonassert")
    testCompileOnly("jakarta.xml.bind:jakarta.xml.bind-api")
}
