plugins {
    id("kiln.java-library")
    `java-test-fixtures`
}

description = "Bulmer Kiln scan subsystem: the protocol, the ScannerAdapter contract and rules, and the contract suite every adapter runs"

dependencies {
    api(project(":kiln-core"))
    // Adapters parse tool output with the same strict mapper, through ToolOutput.
    api("tools.jackson.core:jackson-databind")

    // The suite every scanner adapter in kiln-scanner-service runs.
    testFixturesApi("org.junit.jupiter:junit-jupiter")
    testFixturesImplementation("org.assertj:assertj-core")
}
