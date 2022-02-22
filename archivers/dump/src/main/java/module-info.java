module kala.compress.archivers.dump {
    requires transitive kala.compress.base;

    exports kala.compress.archivers.dump;

    opens kala.compress.archivers.dump to kala.compress.base;
}