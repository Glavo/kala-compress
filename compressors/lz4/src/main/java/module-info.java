@SuppressWarnings("JavaModuleNaming")
module kala.compress.compressors.lz4 {
    requires transitive kala.compress.base;

    exports kala.compress.compressors.lz4;

    provides kala.compress.compressors.BuiltinCompressor
            with kala.compress.compressors.lz4.BlockLZ4Compressor,
                    kala.compress.compressors.lz4.FramedLZ4Compressor;
}