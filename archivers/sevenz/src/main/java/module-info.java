module kala.compress.archivers.sevenz {
    requires transitive kala.compress.base;
    requires static kala.compress.compressors.bzip2;
    requires static kala.compress.compressors.deflate64;
    requires static org.tukaani.xz;

    exports kala.compress.archivers.sevenz;

    opens kala.compress.archivers.sevenz to kala.compress.base;
}