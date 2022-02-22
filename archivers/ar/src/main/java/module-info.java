module kala.compress.archivers.ar {
    requires transitive kala.compress.base;

    exports kala.compress.archivers.ar;

    opens kala.compress.archivers.ar to kala.compress.base;
}