@SuppressWarnings("JavaModuleNaming")
module kala.compress.compressors.lz4 {
    requires transitive kala.compress.base;

    exports kala.compress.compressors.lz4;

    opens kala.compress.compressors.lz4 to kala.compress.base;
}