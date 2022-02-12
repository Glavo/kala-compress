tasks.withType<Jar> {
    archiveBaseName.set("kala-compress-compressors")
}
(publishing.publications["maven"] as MavenPublication).artifactId = "kala-compress-compressors"

subprojects {
    project(":compressors").dependencies.api(this)
    dependencies {
        api(project(":base"))
    }

    tasks.withType<Jar> {
        archiveBaseName.set("kala-compress-compressors-${project.name}")
    }
    (publishing.publications["maven"] as MavenPublication).artifactId = "kala-compress-compressors-${project.name}"
}