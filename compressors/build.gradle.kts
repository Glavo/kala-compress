subprojects {
    project(":kala-compress-compressors").dependencies.api(this)
    dependencies {
        api(project(":kala-compress-base"))
    }
}