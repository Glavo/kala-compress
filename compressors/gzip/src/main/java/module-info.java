module kala.compress.compressors.gzip {
    requires transitive kala.compress.base;

    exports kala.compress.compressors.gzip;

    opens kala.compress.compressors.gzip to kala.compress.base;
}