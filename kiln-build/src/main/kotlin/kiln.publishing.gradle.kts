// Maven publication with the POM metadata Maven Central requires. Signing is on whenever a key is
// provided (KILN_SIGNING_KEY and KILN_SIGNING_PASSWORD); the release workflow in cicd provides it.

plugins {
    `maven-publish`
    signing
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            pluginManager.withPlugin("java-library") {
                from(components["java"])
                versionMapping {
                    usage("java-api") { fromResolutionOf("runtimeClasspath") }
                    usage("java-runtime") { fromResolutionResult() }
                }
            }
            pluginManager.withPlugin("java-platform") {
                from(components["javaPlatform"])
            }
            pom {
                name = project.name
                description = provider { project.description }
                url = "https://bulmerkiln.com"
                licenses {
                    license {
                        name = "Apache-2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                    }
                }
                developers {
                    developer {
                        name = "The Bulmer Kiln Authors"
                        url = "https://github.com/BulmerKiln"
                    }
                }
                scm {
                    url = "https://github.com/BulmerKiln/kiln-components"
                    connection = "scm:git:https://github.com/BulmerKiln/kiln-components.git"
                }
            }
        }
    }
}

val signingKey = providers.environmentVariable("KILN_SIGNING_KEY")
signing {
    isRequired = signingKey.isPresent
    if (signingKey.isPresent) {
        useInMemoryPgpKeys(signingKey.get(), providers.environmentVariable("KILN_SIGNING_PASSWORD").orNull)
        sign(publishing.publications)
    }
}
