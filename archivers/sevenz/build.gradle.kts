dependencies {
    implementation(Dependencies.XZ)
    implementation(project(":compressors:bzip2"))
    implementation(project(":compressors:deflate64"))
}