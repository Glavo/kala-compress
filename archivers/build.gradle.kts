tasks.withType<Jar> {
    archiveBaseName.set("kala-compress-archivers")
}

subprojects {
    project(":archivers").dependencies.api(this)
    dependencies {
        api(project(":base"))
    }

    tasks.withType<Jar> {
        archiveBaseName.set("kala-compress-archivers-$name")
    }
}