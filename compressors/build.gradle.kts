tasks.jar {
    archiveBaseName.set("kala-compress-compressors")
}

subprojects {
    project(":compressors").dependencies.api(this)
    dependencies {
        implementation(project(":base"))
    }

    tasks.jar {
        archiveBaseName.set("kala-compress-compressors-${project.name}")
    }
}