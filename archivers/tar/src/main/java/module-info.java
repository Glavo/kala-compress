module kala.compress.archivers.tar {
    requires transitive kala.compress.base;

    exports kala.compress.archivers.tar;

    provides kala.compress.archivers.BuiltinArchiver
            with kala.compress.archivers.tar.TarArchiver;
}