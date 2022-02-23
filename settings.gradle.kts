@file:Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")

rootProject.name = "kala-compress"

include("base")
project(":base").name = "kala-compress-base"

include("changes")
project(":changes").name = "kala-compress-changes"

include("archivers")
project(":archivers").name = "kala-compress-archivers"

val archivers = listOf(
    "ar", "arj", "cpio", "dump", "sevenz", "tar", "zip"
)
for (archiver in archivers) {
    include(":kala-compress-archivers:$archiver")
    project(":kala-compress-archivers:$archiver").name = "kala-compress-archivers-$archiver"
}

include("compressors")
project(":compressors").name = "kala-compress-compressors"

val compressors = listOf(
    "brotli", "bzip2", "deflate", "deflate64", "gzip", "lz4", "lzma", "pack200", "snappy", "xz", "z", "zstandard"
)

for (compressor in compressors) {
    include(":kala-compress-compressors:$compressor")
    project(":kala-compress-compressors:$compressor").name = "kala-compress-compressors-$compressor"
}


include("filesystems")
project(":filesystems").name = "kala-compress-filesystems"

include(":kala-compress-filesystems:base")
project(":kala-compress-filesystems:base").name = "kala-compress-filesystems-base"
