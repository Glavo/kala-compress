subprojects {
    project(":archivers").dependencies.api(this)
    dependencies {
        implementation(project(":base"))
    }
}