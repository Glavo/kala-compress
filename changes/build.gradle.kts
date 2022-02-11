dependencies {
    implementation(project(":base"))
    implementation(project(":archivers:zip"))
}

tasks.withType<Jar> {
    archiveBaseName.set("kala-compress-changes")
}