plugins {
    id("java-library")
    id("jacoco")
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnlyApi(libs.jetbrains.annotations)
    implementation(libs.xz)
    implementation(libs.zstd.jni)
    implementation(libs.asm)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.junit.vintage.engine)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.memoryfilesystem)
    testImplementation(libs.pax.exam.container.native)
    testImplementation(libs.pax.exam.junit4)
    testImplementation(libs.pax.exam.cm)
    testImplementation(libs.pax.exam.link.mvn)
    testImplementation(libs.felix.framework)
    testImplementation(libs.commons.lang3)
    testImplementation(libs.commons.io)
    testImplementation(libs.javax.inject)
}

tasks.withType<JavaCompile> {
    options.release.set(8)
    options.encoding = "UTF-8"
}

tasks.processTestResources {
    from(tarTree(file("src/test/resources/zstd-tests.tar")))
    from(tarTree(resources.bzip2("src/test/resources/zip64support.tar.bz2")))
}

tasks.test {
    if (javaLauncher.get().metadata.languageVersion.asInt() > 8) {
        jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED")
    }

    useJUnitPlatform()
    testLogging.showStandardStreams = true

    exclude("org/apache/commons/compress/osgi/**")
    if ((project.properties["run-it"] ?: "false") != "true") {
        exclude("**/**IT.class")
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        xml.required.set(true)
        csv.required.set(false)
        html.required.set(false)
    }
}
