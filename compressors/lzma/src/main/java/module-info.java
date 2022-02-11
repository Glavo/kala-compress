module kala.compress.compressors.lzma {
    requires transitive kala.compress.base;
    requires static org.tukaani.xz;

    exports kala.compress.compressors.lzma;

    provides kala.compress.compressors.BuiltinCompressor
            with kala.compress.compressors.lzma.LZMACompressor;
}