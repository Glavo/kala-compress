module kala.compress.compressors.zstandard {
    requires transitive kala.compress.base;
    requires static com.github.luben.zstd_jni;

    exports kala.compress.compressors.zstandard;

    provides kala.compress.compressors.BuiltinCompressor
            with kala.compress.compressors.zstandard.ZstdCompressor;
}