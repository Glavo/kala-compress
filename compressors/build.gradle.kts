subprojects {
    project(":compressors").dependencies.api(this)
    dependencies {
        implementation(project(":base"))
    }
}