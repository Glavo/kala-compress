module kala.compress.compressors.deflate {
    requires transitive kala.compress.base;

    exports kala.compress.compressors.deflate;

    provides kala.compress.compressors.BuiltinCompressor
            with kala.compress.compressors.deflate.DeflateCompressor;
}