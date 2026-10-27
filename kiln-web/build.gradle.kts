plugins {
    id("kiln.java-library")
}

description = "Bulmer Kiln web layer: the request scope every endpoint runs in, and refusals in each format's shape"

dependencies {
    api(project(":kiln-core"))
    api(project(":kiln-format"))
    api(project(":kiln-permissions"))
    // Public auto-configuration classes carry its annotations, so it is part of this module's API.
    api("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    // The servlet API comes from the server at runtime: Tomcat's copy, which is Apache-2.0.
    compileOnlyApi("jakarta.servlet:jakarta.servlet-api")
    compileOnly("org.springframework.boot:spring-boot-tomcat")
    compileOnly("org.apache.tomcat.embed:tomcat-embed-core")

    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.springframework.boot:spring-boot-tomcat")
    testImplementation("org.apache.tomcat.embed:tomcat-embed-core")
    testImplementation("jakarta.servlet:jakarta.servlet-api")
}
