/**
 *
 */
module kala.compress.compressors {
    requires transitive kala.compress.compressors.brotli;
    requires transitive kala.compress.compressors.bzip2;
    requires transitive kala.compress.compressors.deflate;
    requires transitive kala.compress.compressors.deflate64;
    requires transitive kala.compress.compressors.gzip;
    requires transitive kala.compress.compressors.lz4;
    requires transitive kala.compress.compressors.lzma;
    requires transitive kala.compress.compressors.pack200;
    requires transitive kala.compress.compressors.snappy;
    requires transitive kala.compress.compressors.xz;
    requires transitive kala.compress.compressors.z;
    requires transitive kala.compress.compressors.zstandard;
}