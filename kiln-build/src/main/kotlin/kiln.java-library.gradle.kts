// The convention every Kiln library module applies: the kiln.java base, published to Maven
// Central with sources and Javadoc.

plugins {
    `java-library`
    id("kiln.java")
    id("kiln.publishing")
}

java {
    withSourcesJar()
    withJavadocJar()
}
