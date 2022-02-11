@SuppressWarnings("JavaModuleNaming")
module kala.compress.compressors.deflate64 {
    requires transitive kala.compress.base;

    exports kala.compress.compressors.deflate64;

    provides kala.compress.compressors.BuiltinCompressor
            with kala.compress.compressors.deflate64.Deflate64Compressor;
}