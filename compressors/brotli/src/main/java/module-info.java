module kala.compress.compressors.brotli {
    requires transitive kala.compress.base;
    requires static dec;

    exports kala.compress.compressors.brotli;

    provides kala.compress.compressors.BuiltinCompressor
            with kala.compress.compressors.brotli.BrotliCompressor;
}