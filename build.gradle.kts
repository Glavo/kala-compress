plugins {
    id("java-library")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.tukaani:xz:1.10")
    implementation("org.brotli:dec:0.1.2")
    implementation("com.github.luben:zstd-jni:1.5.6-7")
    implementation("commons-io:commons-io:2.17.0")
    implementation("org.apache.commons:commons-lang3:3.17.0")
    implementation("commons-codec:commons-codec:1.17.1")
    implementation("org.ow2.asm:asm:9.7.1")

    val junitVersion = "5.11.0"
    val mockitoVersion = "5.14.2"
    val paxExamVersion = "4.13.5"
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testImplementation("org.junit.vintage:junit-vintage-engine:$junitVersion")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
    testImplementation("org.mockito:mockito-junit-jupiter:$mockitoVersion")
    testImplementation("com.github.marschall:memoryfilesystem:2.8.1")
    testImplementation("org.ops4j.pax.exam:pax-exam-container-native:$paxExamVersion")
    testImplementation("org.ops4j.pax.exam:pax-exam-junit4:$paxExamVersion")
    testImplementation("org.ops4j.pax.exam:pax-exam-cm:$paxExamVersion")
    testImplementation("org.ops4j.pax.exam:pax-exam-link-mvn:$paxExamVersion")
    testImplementation("org.apache.felix:org.apache.felix.framework:7.0.5")
    testImplementation("javax.inject:javax.inject:1")
}

tasks.test {
    useJUnitPlatform()
    testLogging.showStandardStreams = true
}
