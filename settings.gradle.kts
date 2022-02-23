@file:Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")

rootProject.name = "kala-compress"

include("base", "changes")

val archivers = listOf(
    "ar", "arj", "cpio", "dump", "sevenz", "tar", "zip"
)
for (archiver in archivers) {
    include(":archivers:$archiver")
}


val compressors = listOf(
    "brotli", "bzip2", "deflate", "deflate64", "gzip", "lz4", "lzma", "pack200", "snappy", "xz", "z", "zstandard"
)

for (compressor in compressors) {
    include(":compressors:$compressor")
}


include("filesystems")
include(":filesystems:base")
