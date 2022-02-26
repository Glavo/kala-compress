package kala.compress.filesystems;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.attribute.FileStoreAttributeView;
import java.util.Objects;

public abstract class ArchiveFileStore extends FileStore {
    protected abstract ArchiveFileSystem getArchiveFileSystem();

    @Override
    public String name() {
        return getArchiveFileSystem() + "/";
    }

    @Override
    public boolean isReadOnly() {
        return getArchiveFileSystem().isReadOnly();
    }

    @Override
    public long getTotalSpace() throws IOException {
        return Files.size(getArchiveFileSystem().archiveFilePath);
    }

    @Override
    public long getUsableSpace() throws IOException {
        return isReadOnly() ? 0 : Files.getFileStore(getArchiveFileSystem().archiveFilePath).getUsableSpace();
    }

    @Override
    public long getUnallocatedSpace() throws IOException {
        return isReadOnly() ? 0 : Files.getFileStore(getArchiveFileSystem().archiveFilePath).getUnallocatedSpace();
    }

    @Override
    public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
        Objects.requireNonNull(type);
        return null;
    }

    @Override
    public Object getAttribute(String attribute) throws IOException {
        switch (attribute) {
            case "totalSpace":
                return getTotalSpace();
            case "usableSpace":
                return getUsableSpace();
            case "unallocatedSpace":
                return getUnallocatedSpace();
            default:
                throw new UnsupportedOperationException("does not support the given attribute");
        }
    }
}
