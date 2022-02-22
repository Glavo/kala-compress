module kala.compress.compressors.zstandard {
    requires transitive kala.compress.base;
    requires static com.github.luben.zstd_jni;

    exports kala.compress.compressors.zstandard;

    opens kala.compress.compressors.zstandard to kala.compress.base;
}