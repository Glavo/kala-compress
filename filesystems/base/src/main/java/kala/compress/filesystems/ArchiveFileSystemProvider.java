package kala.compress.filesystems;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.Map;

public abstract class ArchiveFileSystemProvider extends FileSystemProvider {
    private final String scheme;

    private final Map<Path, ArchiveFileSystem> filesystems = new HashMap<>();

    protected ArchiveFileSystemProvider(String scheme) {
        this.scheme = scheme;
    }

    protected Path getArchivePath(URI uri) {
        String scheme = uri.getScheme();
        if (!getScheme().equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("URI scheme is not '" + getScheme() + "'");
        }

        String str = uri.toString().substring(scheme.length() + 1);

        int idx = str.lastIndexOf('!');
        if (idx < 0) {
            throw new IllegalArgumentException();
        }
        str = str.substring(0, idx);

        return Paths.get(URI.create(str));
    }

    @Override
    public String getScheme() {
        return scheme;
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        Path archivePath;
        try {
            archivePath = getArchivePath(uri).toRealPath();
        } catch (IOException e) {
            throw (FileSystemNotFoundException) new FileSystemNotFoundException().initCause(e);
        }

        synchronized (filesystems) {
            ArchiveFileSystem fs = filesystems.get(archivePath);
            if (fs == null) {
                throw new FileSystemNotFoundException();
            }
            return fs;
        }
    }

    @Override
    public Path getPath(URI uri) {
        FileSystem fs = getFileSystem(uri);

        String str = uri.toString();
        int idx = str.lastIndexOf('!');
        if (idx < 0) {
            throw new IllegalArgumentException();
        }

        str = str.substring(idx + 1);

        return fs.getPath(ArchiveFileSystemPath.decodePath(str));
    }
}
