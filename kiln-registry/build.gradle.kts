plugins {
    id("kiln.java-library")
}

description = "Bulmer Kiln registry: namespaces, blobs, packages, blocks and tombstones, shared by every format"

dependencies {
    api(project(":kiln-core"))
    api(project(":kiln-database"))
    api(project(":kiln-format"))
    api(project(":kiln-permissions"))
    api("org.jooq:jooq")
    // Public auto-configuration classes carry its annotations, so it is part of this module's API.
    api("org.springframework.boot:spring-boot-autoconfigure")
    // jOOQ's settings carry JAXB annotations; the compiler needs them to read its classes, nothing does at runtime.
    compileOnly("jakarta.xml.bind:jakarta.xml.bind-api")
    testCompileOnly("jakarta.xml.bind:jakarta.xml.bind-api")

    testImplementation(testFixtures(project(":kiln-database")))
    testImplementation("org.springframework.boot:spring-boot-test")
}
