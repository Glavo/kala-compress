plugins {
    `java-library`
}

val buildType = project.findProperty("buildType")?.toString()?.toUpperCase() ?: "SNAPSHOT"
val baseVersion = "1.21.0.1-alpha1"

allprojects {
    apply {
        plugin("java-library")
    }

    group = "org.glavo.kala"
    description = "Kala Compress"

    version = when (buildType) {
        "RELEASE" -> baseVersion
        "SNAPSHOT" -> "$baseVersion-SNAPSHOT"
        "NIGHTLY" -> TODO()
        else -> throw UnsupportedOperationException()
    }

    repositories {
        maven(url = "https://repository.apache.org/snapshots")
        mavenCentral()
    }

    java {
        withSourcesJar()
    }

    tasks.compileJava {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

}

subprojects {
    rootProject.dependencies.api(this)
}

dependencies {
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
    testImplementation("org.glavo:pack200:0.3.0")
    testImplementation(Dependencies.XZ)
    testImplementation(Dependencies.ZSTD_JNI)
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
