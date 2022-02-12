dependencies {
    implementation(project(":base"))
    implementation(project(":archivers:zip"))
}

(publishing.publications["maven"] as MavenPublication).artifactId = "kala-compress-changes"
tasks.withType<Jar> {
    archiveBaseName.set("kala-compress-changes")
}