module kala.compress.compressors.lzma {
    requires transitive kala.compress.base;
    requires static org.tukaani.xz;

    exports kala.compress.compressors.lzma;

    opens kala.compress.compressors.lzma to kala.compress.base;
}