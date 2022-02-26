package kala.compress.filesystems.zip;

import kala.compress.filesystems.ArchiveFileSystemOptions;

import java.util.Map;

public final class ZipFileSystemOptions extends ArchiveFileSystemOptions {

    public static final String FORCE_ZIP64_END = "forceZIP64End";

    ZipFileSystemOptions(Map<String, ?> options) {
        super(options);
    }

}
