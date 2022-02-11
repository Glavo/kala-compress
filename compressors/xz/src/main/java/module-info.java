module kala.compress.compressors.xz {
    requires transitive kala.compress.base;
    requires static org.tukaani.xz;

    exports kala.compress.compressors.xz;

    provides kala.compress.compressors.BuiltinCompressor
            with kala.compress.compressors.xz.XZCompressor;
}