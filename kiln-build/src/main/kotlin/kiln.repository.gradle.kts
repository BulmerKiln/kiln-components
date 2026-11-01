// Applied at the root of kiln-components: the checks that span every module.
// - The README check (BUILD-8)
// - Dependency analysis (BUILD-15): no unused dependency, and none declared at the wrong scope

plugins {
    id("kiln.readme-check")
    id("kiln.dependency-health")
}

dependencyAnalysis {
    issues {
        // Its dependencies are used by scanning their classes, which analysis can't see.
        project(":kiln-architecture") {
            onAny {
                severity("ignore")
            }
        }
    }
}
