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

    testImplementation("org.mockito:mockito-core:")
}
