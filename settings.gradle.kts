@file:Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")

rootProject.name = "kala-compress"

include("base", "changes")

for (archiver in file("archivers").listFiles()) {
    if (archiver.isDirectory && archiver.name != "build") {
        include(":archivers:${archiver.name}")
    }
}

for (compressor in file("compressors").listFiles()) {
    if (compressor.isDirectory && compressor.name != "build") {
        include(":compressors:${compressor.name}")
    }
}