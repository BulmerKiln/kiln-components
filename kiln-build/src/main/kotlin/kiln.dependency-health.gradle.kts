// Dependency analysis (BUILD-15): no unused dependency, and none declared at the wrong scope.
// Applied at the root of a repository; buildHealth covers every project in it.

plugins {
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
    }
}

tasks.named("check") {
    dependsOn("buildHealth")
}
