dependencies {
    implementation(Dependencies.XZ)
    compileOnly(project(":compressors:bzip2"))
    compileOnly(project(":compressors:deflate64"))
}