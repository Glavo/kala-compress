module kala.compress.compressors.snappy {
    requires transitive kala.compress.base;

    exports kala.compress.compressors.snappy;

    provides kala.compress.compressors.BuiltinCompressor
            with kala.compress.compressors.snappy.SnappyCompressor,
                    kala.compress.compressors.snappy.FramedSnappyCompressor;
}