@SuppressWarnings("JavaModuleNaming")
module kala.compress.compressors.bzip2 {
    requires transitive kala.compress.base;

    exports kala.compress.compressors.bzip2;

    opens kala.compress.compressors.bzip2 to kala.compress.base;
}