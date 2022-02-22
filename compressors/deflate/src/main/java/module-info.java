module kala.compress.compressors.deflate {
    requires transitive kala.compress.base;

    exports kala.compress.compressors.deflate;

    opens kala.compress.compressors.deflate to kala.compress.base;
}