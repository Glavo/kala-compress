@SuppressWarnings("JavaModuleNaming")
module kala.compress.compressors.deflate64 {
    requires transitive kala.compress.base;

    exports kala.compress.compressors.deflate64;

    opens kala.compress.compressors.deflate64 to kala.compress.base;
}