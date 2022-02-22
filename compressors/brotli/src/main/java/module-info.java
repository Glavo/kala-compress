module kala.compress.compressors.brotli {
    requires transitive kala.compress.base;

    exports kala.compress.compressors.brotli;
    exports kala.compress.compressors.brotli.dec;

    opens kala.compress.compressors.brotli to kala.compress.base;
}