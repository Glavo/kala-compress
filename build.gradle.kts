plugins {
    java
}

group = "org.glavo"
description = "Glavo Compress"

version = "1.21.0.1"

val buildType = project.findProperty("buildType")?.toString()?.toUpperCase() ?: "SNAPSHOT"

when (buildType) {
    "RELEASE" -> {}
    "SNAPSHOT" -> version = "$version-SNAPSHOT"
    "NIGHTLY" -> TODO()
}

repositories {
    maven(url = "https://repository.apache.org/snapshots")
    mavenCentral()
}

dependencies {
    implementation("com.github.luben:zstd-jni:1.5.0-2")
    implementation("org.brotli:dec:0.1.2")
    implementation("org.tukaani:xz:1.9")
    implementation("asm:asm:3.2")

    testImplementation("org.hamcrest:hamcrest:2.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:3.11.1")
    testImplementation("com.github.marschall:memoryfilesystem:2.1.0")
    testImplementation("org.ops4j.pax.exam:pax-exam-container-native:4.13.1")
    testImplementation("org.ops4j.pax.exam:pax-exam-junit4:4.13.1")
    testImplementation("org.ops4j.pax.exam:pax-exam-cm:4.13.1")
    testImplementation("org.ops4j.pax.exam:pax-exam-link-mvn:4.13.1")
    testImplementation("org.apache.felix:org.apache.felix.framework:7.0.0")
    testImplementation("javax.inject:javax.inject:1")
    testImplementation("org.slf4j:slf4j-api:1.7.30")
}


java {
    withSourcesJar()
}

tasks.compileJava {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}

tasks.test {
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)

    if (project.findProperty("run-tarit") != "true") {
        exclude("**/tar/*IT.class")
    }
    if (project.findProperty("run-zipit") != "true") {
        exclude("**/zip/*IT.class")
    }
}

tasks.processTestResources {
    from(tarTree(file("src/test/resources/zstd-tests.tar")))
    from(tarTree(resources.bzip2("src/test/resources/zip64support.tar.bz2")))
}
