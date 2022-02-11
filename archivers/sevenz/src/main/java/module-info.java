module kala.compress.archivers.sevenz {
    requires transitive kala.compress.base;
    requires static org.tukaani.xz; // Do we need static?
    requires static kala.compress.compressors.bzip2;
    requires static kala.compress.compressors.deflate64;

    exports kala.compress.archivers.sevenz;

    provides kala.compress.archivers.BuiltinArchiver
            with kala.compress.archivers.sevenz.SevenZArchiver;
}