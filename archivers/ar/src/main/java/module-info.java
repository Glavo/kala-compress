module kala.compress.archivers.ar {
    requires transitive kala.compress.base;

    exports kala.compress.archivers.ar;

    provides kala.compress.archivers.BuiltinArchiver
            with kala.compress.archivers.ar.ArArchiver;
}