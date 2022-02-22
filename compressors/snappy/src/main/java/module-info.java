module kala.compress.compressors.snappy {
    requires transitive kala.compress.base;

    exports kala.compress.compressors.snappy;

    opens kala.compress.compressors.snappy to kala.compress.base;
}