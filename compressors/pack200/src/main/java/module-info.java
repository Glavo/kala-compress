@SuppressWarnings("JavaModuleNaming")
module kala.compress.compressors.pack200 {
    requires transitive kala.compress.base;

    exports kala.compress.compressors.pack200;

    provides kala.compress.compressors.BuiltinCompressor
            with kala.compress.compressors.pack200.Pack200Compressor;
}