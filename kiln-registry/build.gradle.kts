plugins {
    id("kiln.java-library")
    `java-test-fixtures`
}

description = "Bulmer Kiln registry: namespaces, blobs, packages, blocks and tombstones, shared by every format"

dependencies {
    api(project(":kiln-core"))
    api(project(":kiln-database"))
    api(project(":kiln-format"))
    api(project(":kiln-permissions"))
    // Public auto-configuration classes carry its annotations, so it is part of this module's API.
    api("org.springframework.boot:spring-boot-autoconfigure")

    testFixturesApi(project(":kiln-database"))
    testFixturesImplementation(testFixtures(project(":kiln-database")))
    // The generated mappers it constructs carry MapStruct's annotations, which the compiler reads.
    testFixturesCompileOnly("org.mapstruct:mapstruct")

    testImplementation(testFixtures(project(":kiln-database")))
    testImplementation("org.springframework.boot:spring-boot-test")
}
