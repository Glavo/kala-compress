@SuppressWarnings("JavaModuleNaming")
module kala.compress.compressors.pack200 {
    requires transitive kala.compress.base;

    exports kala.compress.compressors.pack200;

    opens kala.compress.compressors.pack200 to kala.compress.base;
}