plugins {
    id("kiln.java-library")
}

description = "Bulmer Kiln S3 blob store, with presigned redirects"

dependencies {
    api(project(":kiln-storage"))
    api("org.springframework.boot:spring-boot")
}
