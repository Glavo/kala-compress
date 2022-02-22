module kala.compress.compressors.z {
    requires transitive kala.compress.base;

    exports kala.compress.compressors.z;

    opens kala.compress.compressors.z to kala.compress.base;
}