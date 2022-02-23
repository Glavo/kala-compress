subprojects {
    project(":kala-compress-archivers").dependencies.api(this)
    dependencies {
        api(project(":kala-compress-base"))
    }
}