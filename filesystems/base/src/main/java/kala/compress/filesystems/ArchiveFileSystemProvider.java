package kala.compress.filesystems;

import java.nio.file.spi.FileSystemProvider;

public abstract class ArchiveFileSystemProvider extends FileSystemProvider {
    private final String scheme;

    protected ArchiveFileSystemProvider(String scheme) {
        this.scheme = scheme;
    }

    @Override
    public String getScheme() {
        return scheme;
    }
}
