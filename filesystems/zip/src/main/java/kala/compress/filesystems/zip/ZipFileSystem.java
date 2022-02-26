package kala.compress.filesystems.zip;

import kala.compress.filesystems.ArchiveFileSystem;
import kala.compress.filesystems.ArchiveFileSystemPath;
import kala.compress.filesystems.ArchiveFileSystemProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

public final class ZipFileSystem extends ArchiveFileSystem {
    public ZipFileSystem(Path archiveFilePath, ZipFileSystemOptions options) throws IOException {
        super(archiveFilePath);

        BasicFileAttributes attributes = Files.readAttributes(archiveFilePath, BasicFileAttributes.class);


    }

    @Override
    protected ArchiveFileSystemPath createPath(boolean isAbsolute, String[] elements) {
        return null; // TODO
    }

    @Override
    protected ArchiveFileSystemProvider getArchiveFileSystemProvider() {
        return null; // TODO
    }

    @Override
    public void close() throws IOException {
        // TODO
    }

    @Override
    public boolean isOpen() {
        return false; // TODO
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return null; // TODO
    }
}
