module kala.compress.archivers.tar {
    requires transitive kala.compress.base;

    exports kala.compress.archivers.tar;

    opens kala.compress.archivers.tar to kala.compress.base;
}