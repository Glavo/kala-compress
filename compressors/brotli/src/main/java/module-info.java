module kala.compress.compressors.brotli {
    requires transitive kala.compress.base;

    exports kala.compress.compressors.brotli;
    exports kala.compress.compressors.brotli.dec;

    provides kala.compress.compressors.BuiltinCompressor
            with kala.compress.compressors.brotli.BrotliCompressor;
}