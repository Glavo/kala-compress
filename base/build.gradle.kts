tasks.withType<Jar> {
    archiveBaseName.set("kala-compress-base")
}

(publishing.publications["maven"] as MavenPublication).artifactId = "kala-compress-base"