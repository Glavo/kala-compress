dependencies {
    implementation(Dependencies.XZ)
    compileOnly(project(":kala-compress-compressors:kala-compress-compressors-bzip2"))
    compileOnly(project(":kala-compress-compressors:kala-compress-compressors-deflate64"))
}