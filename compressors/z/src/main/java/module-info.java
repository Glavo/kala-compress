module kala.compress.compressors.z {
    requires transitive kala.compress.base;

    exports kala.compress.compressors.z;

    provides kala.compress.compressors.BuiltinCompressor
            with kala.compress.compressors.z.ZCompressor;
}