@SuppressWarnings("JavaModuleNaming")
module kala.compress.compressors.bzip2 {
    requires transitive kala.compress.base;

    exports kala.compress.compressors.bzip2;

    provides kala.compress.compressors.BuiltinCompressor
            with kala.compress.compressors.bzip2.BZip2Compressor;
}