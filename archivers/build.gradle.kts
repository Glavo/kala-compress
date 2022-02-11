tasks.jar {
    archiveBaseName.set("kala-compress-archivers")
}

subprojects {
    project(":archivers").dependencies.api(this)
    dependencies {
        implementation(project(":base"))
    }

    tasks.jar {
        archiveBaseName.set("kala-compress-archivers-$name")
    }
}