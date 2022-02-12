import java.io.RandomAccessFile
import java.util.Properties

plugins {
    `java-library`
    `maven-publish`
    signing
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
}

val buildType = project.findProperty("buildType")?.toString()?.toUpperCase() ?: "SNAPSHOT"
val baseVersion = "1.21.0.1-beta2"

loadMavenPublishProperties()

allprojects {
    if (project.name == "buildSrc") return@allprojects

    apply {
        plugin("java-library")
        plugin("maven-publish")
        plugin("signing")
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
        modularity.inferModulePath.set(true)
        options.release.set(9)
        options.isWarnings = false
        doLast {
            val tree = fileTree(destinationDirectory)
            tree.include("**/*.class")
            tree.exclude("module-info.class")
            tree.forEach {
                RandomAccessFile(it, "rw").use { rf ->
                    rf.seek(7)   // major version
                    rf.write(52)   // java 8
                    rf.close()
                }
            }
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    tasks.javadoc {
        isEnabled = false
        (options as StandardJavadocDocletOptions).apply {
            encoding = "UTF-8"
            addBooleanOption("html5", true)
            addStringOption("Xdoclint:none", "-quiet")
        }
    }

    val javadocJar = tasks.create<Jar>("javadocJar") {
        group = "build"
        archiveClassifier.set("javadoc")
    }

    tasks.withType<Jar> {
        metaInf {
            from(rootProject.file("LICENSE.txt"))
        }
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
                artifact(javadocJar)

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
    if (project.name == "buildSrc") return@subprojects
    rootProject.dependencies.api(this)
}

dependencies {
    testImplementation("org.hamcrest:hamcrest:2.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:3.11.1")
    testImplementation("com.github.marschall:memoryfilesystem:2.1.0")
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

fun loadMavenPublishProperties() {
    var secretPropsFile = project.rootProject.file("gradle/maven-central-publish.properties")
    if (!secretPropsFile.exists()) {
        secretPropsFile =
            file(System.getProperty("user.home")).resolve(".gradle").resolve("maven-central-publish.properties")
    }

    if (secretPropsFile.exists()) {
        // Read local.properties file first if it exists
        val p = Properties()
        secretPropsFile.reader().use {
            p.load(it)
        }

        p.forEach { (name, value) ->
            rootProject.ext[name.toString()] = value
        }
    }

    listOf(
        "sonatypeUsername" to "SONATYPE_USERNAME",
        "sonatypePassword" to "SONATYPE_PASSWORD",
        "sonatypeStagingProfileId" to "SONATYPE_STAGING_PROFILE_ID",
        "signing.keyId" to "SIGNING_KEY_ID",
        "signing.password" to "SIGNING_PASSWORD",
        "signing.key" to "SIGNING_KEY"
    ).forEach { (p, e) ->
        if (!rootProject.ext.has(p)) {
            rootProject.ext[p] = System.getenv(e)
        }
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
