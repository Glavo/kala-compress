subprojects {
    project(":kala-compress-filesystems").dependencies.api(this)

    dependencies {
        api(project(":kala-compress-base"))
        if (project.name != "kala-compress-filesystems-base") {
            api(project(":kala-compress-filesystems:kala-compress-filesystems-base"))
        }
    }
}