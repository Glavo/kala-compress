module kala.compress.archivers.dump {
    requires transitive kala.compress.base;

    exports kala.compress.archivers.dump;

    provides kala.compress.archivers.BuiltinArchiver
            with kala.compress.archivers.dump.DumpArchiver;
}