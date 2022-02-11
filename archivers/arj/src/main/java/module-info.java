module kala.compress.archivers.arj {
    requires transitive kala.compress.base;

    exports kala.compress.archivers.arj;

    provides kala.compress.archivers.BuiltinArchiver
            with kala.compress.archivers.arj.ArjArchiver;
}