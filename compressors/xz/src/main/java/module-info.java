module kala.compress.compressors.xz {
    requires transitive kala.compress.base;
    requires static org.tukaani.xz;

    exports kala.compress.compressors.xz;

    opens kala.compress.compressors.xz to kala.compress.base;
}