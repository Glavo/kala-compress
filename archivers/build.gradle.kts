tasks.jar {
    archiveBaseName.set("kala-compress-archivers")
}

subprojects {
    project(":archivers").dependencies.api(this)
    dependencies {
        api(project(":base"))
    }

    tasks.jar {
        archiveBaseName.set("kala-compress-archivers-$name")
    }
}