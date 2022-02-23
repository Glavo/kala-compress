subprojects {
    project(":kala-compress-filesystems").dependencies.api(this)

    dependencies {
        api(project(":kala-compress-base"))
    }
}