module kala.compress.archivers.cpio {
    requires transitive kala.compress.base;

    exports kala.compress.archivers.cpio;

    provides kala.compress.archivers.BuiltinArchiver
            with kala.compress.archivers.cpio.CpioArchiver;
}