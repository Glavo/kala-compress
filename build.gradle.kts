/*
 * Copyright 2024 Glavo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import kala.template.TemplateEngine
import java.util.Properties
import kotlin.math.max

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath("org.glavo.kala:kala-template:0.2.0")
    }
}

plugins {
    id("java-library")
    id("jacoco")
    id("maven-publish")
    id("signing")
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
    id("org.glavo.load-maven-publish-properties") version "0.1.0"
    id("org.glavo.compile-module-info-plugin") version "2.0"
}

allprojects {
    apply {
        plugin("java-library")
        plugin("maven-publish")
        plugin("signing")
        plugin("org.glavo.compile-module-info-plugin")
    }

    group = "org.glavo.kala"
    description = "Kala Compress"

    version = "1.27.1-1" + "SNAPSHOT"

    repositories {
        mavenCentral()
    }

    dependencies {
        compileOnlyApi(rootProject.libs.jetbrains.annotations)
    }

    java {
        withSourcesJar()
    }

    tasks.compileJava {
        sourceCompatibility = "9"
        options.isWarnings = false
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(8)
    }

    tasks.javadoc {
        isEnabled = false
        (options as StandardJavadocDocletOptions).apply {
            encoding = "UTF-8"
            addBooleanOption("html5", true)
            addStringOption("Xdoclint:none", "-quiet")
        }
    }

    java {
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<GenerateModuleMetadata> {
        enabled = false
    }

    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                groupId = project.group.toString()
                version = project.version.toString()
                artifactId = project.name
                from(components["java"])

                pom {
                    name.set(project.name)
                    description.set(project.description)
                    url.set("https://github.com/Glavo/kala-compress")
                    licenses {
                        license {
                            name.set("Apache 2.0")
                            url.set("https://github.com/Glavo/kala-compress/blob/main/LICENSE.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("glavo")
                            name.set("Glavo")
                            email.set("zjx001202@gmail.com")
                        }
                    }
                    scm {
                        url.set("https://github.com/Glavo/kala-compress")
                    }
                }
            }
        }
    }

    if (rootProject.ext.has("signing.key")) {
        signing {
            useInMemoryPgpKeys(
                rootProject.ext["signing.keyId"].toString(),
                rootProject.ext["signing.key"].toString(),
                rootProject.ext["signing.password"].toString(),
            )
            sign(publishing.publications["maven"])
        }
    }

}

subprojects {
    rootProject.dependencies.api(this)

    val base = project(":kala-compress-base")
    if (project != base) {
        dependencies.api(base)
    }
}

dependencies {
    testImplementation(libs.xz)
    testImplementation(libs.zstd.jni)
    testImplementation(libs.asm)
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

tasks.processTestResources {
    from(tarTree(file("src/test/resources/zstd-tests.tar")))
    from(tarTree(resources.bzip2("src/test/resources/zip64support.tar.bz2")))
}

tasks.test {
    if (javaLauncher.get().metadata.languageVersion.asInt() > 8) {
        jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED")
    }

    maxParallelForks = max(Runtime.getRuntime().availableProcessors() / 4, 1)

    useJUnitPlatform()
    testLogging.showStandardStreams = true

    if (project.findProperty("run-tarit") != "true") {
        exclude("**/tar/*IT.class")
    }
    if (project.findProperty("run-zipit") != "true") {
        exclude("**/zip/*IT.class")
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

nexusPublishing {
    repositories {
        sonatype {
            stagingProfileId.set(rootProject.ext["sonatypeStagingProfileId"].toString())
            username.set(rootProject.ext["sonatypeUsername"].toString())
            password.set(rootProject.ext["sonatypePassword"].toString())
        }
    }
}

tasks.create("generateReadMe") {
    group = "documentation"

    val templateFile = file("README.md.template")
    val outputFile = file("README.md")
    val propertiesFile = file("README.properties")

    inputs.files(templateFile, propertiesFile)
    outputs.file(outputFile)


    doLast {
        val properties = Properties()
        file("README.properties").reader().use { properties.load(it) }

        TemplateEngine.builder().tag("\$\${", "}").build()
            .process(templateFile.toPath(), outputFile.toPath(), properties)
    }
}