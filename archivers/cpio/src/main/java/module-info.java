module kala.compress.archivers.cpio {
    requires transitive kala.compress.base;

    exports kala.compress.archivers.cpio;

    opens kala.compress.archivers.cpio to kala.compress.base;
}