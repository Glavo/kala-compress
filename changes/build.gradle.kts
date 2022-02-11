dependencies {
    implementation(project(":base"))
    implementation(project(":archivers:zip"))
}

tasks.jar {
    archiveBaseName.set("kala-compress-changes")
}