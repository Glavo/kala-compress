subprojects {
    project(":filesystems").dependencies.api(this)

    dependencies {
        api(project(":base"))

        if (name != "base") {
            api(project(":filesystems:base"))
        }
    }

}