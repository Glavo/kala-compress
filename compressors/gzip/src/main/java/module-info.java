module kala.compress.compressors.gzip {
    requires transitive kala.compress.base;

    exports kala.compress.compressors.gzip;

    provides kala.compress.compressors.BuiltinCompressor
            with kala.compress.compressors.gzip.GzipCompressor;
}