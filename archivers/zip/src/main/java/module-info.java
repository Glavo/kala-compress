module kala.compress.archivers.zip {
    requires transitive kala.compress.base;
    requires static kala.compress.compressors.bzip2;
    requires static kala.compress.compressors.deflate64;

    exports kala.compress.archivers.jar;
    exports kala.compress.archivers.zip;

    provides kala.compress.archivers.BuiltinArchiver
            with kala.compress.archivers.jar.JarArchiver,
                    kala.compress.archivers.zip.ZipArchiver;
}