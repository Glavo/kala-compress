module kala.compress.archivers.zip {
    requires transitive kala.compress.base;
    requires static kala.compress.compressors.bzip2;
    requires static kala.compress.compressors.deflate64;

    exports kala.compress.archivers.jar;
    exports kala.compress.archivers.zip;

    opens kala.compress.archivers.jar to kala.compress.base;
    opens kala.compress.archivers.zip to kala.compress.base;
}