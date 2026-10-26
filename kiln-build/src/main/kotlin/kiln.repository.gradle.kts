// Applied at the root of kiln-components: the checks that span every module.
// - The README check (BUILD-8)
// - Dependency analysis (BUILD-15): no unused dependency, and none declared at the wrong scope

plugins {
    id("kiln.readme-check")
    id("com.autonomousapps.dependency-analysis")
}

dependencyAnalysis {
    issues {
        all {
            onAny {
                severity("fail")
            }
            // Modules rely on libraries that arrive through others, such as Spring through
            // spring-boot-autoconfigure; requiring each to be declared again adds noise, not safety.
            onUsedTransitiveDependencies {
                severity("ignore")
            }
            // The conventions give every module the same annotations and test stack, used or not.
            onUnusedDependencies {
                exclude(
                    "org.jspecify:jspecify",
                    "com.google.errorprone:error_prone_annotations",
                    "org.junit.jupiter:junit-jupiter",
                    "org.assertj:assertj-core",
                    "net.jqwik:jqwik",
                    "com.code-intelligence:jazzer-junit",
                )
            }
            // JSpecify annotations are part of each module's public API, so they stay api.
            onCompileOnly {
                exclude("org.jspecify:jspecify")
            }
        }
        // Its dependencies are used by scanning their classes, which analysis can't see.
        project(":kiln-architecture") {
            onAny {
                severity("ignore")
            }
        }
    }
}

tasks.named("check") {
    dependsOn("buildHealth")
}
