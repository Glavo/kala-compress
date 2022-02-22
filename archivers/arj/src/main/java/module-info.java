module kala.compress.archivers.arj {
    requires transitive kala.compress.base;

    exports kala.compress.archivers.arj;

    opens kala.compress.archivers.arj to kala.compress.base;
}