tasks.withType<Jar> {
    archiveBaseName.set("kala-compress-archivers")
}
(publishing.publications["maven"] as MavenPublication).artifactId = "kala-compress-archivers"

subprojects {
    project(":archivers").dependencies.api(this)
    dependencies {
        api(project(":base"))
    }

    tasks.withType<Jar> {
        archiveBaseName.set("kala-compress-archivers-${project.name}")
    }
    (publishing.publications["maven"] as MavenPublication).artifactId = "kala-compress-archivers-${project.name}"
}