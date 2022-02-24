package kala.compress.filesystems;

import kala.compress.filesystems.utils.StringArrayUtils;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Iterator;

public abstract class ArchiveFileSystemPath implements Path {
    protected final boolean isAbsolute;
    protected final String[] elements;

    private String display;

    protected ArchiveFileSystemPath(boolean isAbsolute, String[] elements) {
        this.isAbsolute = isAbsolute;
        this.elements = elements;
    }

    public static String encodePath(String path) {
        try {
            return URLEncoder.encode(path, "UTF-8").replace("!", "%21");
        } catch (UnsupportedEncodingException e) {
            throw new InternalError(e);
        }
    }

    public static String decodePath(String path) {
        try {
            return URLDecoder.decode(path, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new InternalError(e);
        }
    }

    protected abstract ArchiveFileSystem getArchiveFileSystem();

    protected ArchiveFileSystemPath checkPath(Path path) {
        if (getFileSystem().provider() != path.getFileSystem().provider()) {
            throw new ProviderMismatchException();
        }

        return ((ArchiveFileSystemPath) path);
    }

    @Override
    public FileSystem getFileSystem() {
        return getArchiveFileSystem();
    }

    @Override
    public boolean isAbsolute() {
        return isAbsolute;
    }

    @Override
    public Path getRoot() {
        return getArchiveFileSystem().getRoot();
    }

    @Override
    public Path getFileName() {
        if (elements.length == 0) {
            return null;
        } else {
            return getArchiveFileSystem().createPath(false, new String[]{elements[elements.length - 1]});
        }
    }

    @Override
    public Path getParent() {
        if (elements.length == 0) {
            return null;
        } else if (elements.length == 1) {
            return isAbsolute ? getRoot() : null;
        } else {
            return getArchiveFileSystem().createPath(isAbsolute, Arrays.copyOfRange(elements, 0, elements.length - 1));
        }
    }

    @Override
    public int getNameCount() {
        return elements.length;
    }

    @Override
    public Path getName(int index) {
        if (index < 0 || index >= getNameCount()) {
            throw new IllegalArgumentException();
        }

        return getArchiveFileSystem().createPath(false, StringArrayUtils.single(elements[index]));
    }

    @Override
    public Path subpath(int beginIndex, int endIndex) {
        if (beginIndex < 0 || beginIndex >= elements.length
                || beginIndex >= endIndex || endIndex > elements.length) {
            throw new IllegalArgumentException();
        }

        String[] newElements;

        if (beginIndex == 0 && endIndex == elements.length) {
            if (!isAbsolute) {
                return this;
            }
            newElements = elements;
        } else {
            newElements = Arrays.copyOfRange(elements, beginIndex, endIndex);
        }

        return getArchiveFileSystem().createPath(false, newElements);
    }

    @Override
    public boolean startsWith(Path other) {
        if (getFileSystem() != other.getFileSystem()) {
            return false;
        }
        ArchiveFileSystemPath otherPath = (ArchiveFileSystemPath) other;

        if (this.isAbsolute != otherPath.isAbsolute
                || this.elements.length < otherPath.elements.length) {
            return false;
        }

        for (int i = 0; i < otherPath.elements.length; i++) {
            if (!elements[i].equals(otherPath.elements[i])) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean startsWith(String other) {
        return startsWith(getFileSystem().getPath(other));
    }

    @Override
    public boolean endsWith(Path other) {
        if (getFileSystem() != other.getFileSystem()) {
            return false;
        }
        ArchiveFileSystemPath otherPath = (ArchiveFileSystemPath) other;

        if (otherPath.isAbsolute()) {
            return this.equals(otherPath);
        }

        if (this.elements.length < otherPath.elements.length) {
            return false;
        }

        int offset = this.elements.length - otherPath.elements.length;
        for (int i = 0; i < otherPath.elements.length; i++) {
            if (!this.elements[i + offset].equals(otherPath.elements[i])) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean endsWith(String other) {
        return endsWith(getFileSystem().getPath(other));
    }

    @Override
    public Path normalize() {
        return this;
    }

    @Override
    public Path resolve(Path other) {
        ArchiveFileSystemPath otherPath = checkPath(other);

        if (otherPath.isAbsolute) {
            return otherPath;
        }

        return getArchiveFileSystem().createPath(isAbsolute, StringArrayUtils.concat(this.elements, otherPath.elements));
    }

    @Override
    public Path resolve(String other) {
        return resolve(getFileSystem().getPath(other));
    }

    @Override
    public Path resolveSibling(Path other) {
        if (other == null)
            throw new NullPointerException();
        Path parent = getParent();
        return (parent == null) ? other : parent.resolve(other);
    }

    @Override
    public Path resolveSibling(String other) {
        return resolveSibling(getFileSystem().getPath(other));
    }

    @Override
    public Path relativize(Path other) {
        ArchiveFileSystemPath otherPath = checkPath(other);

        if (isAbsolute != otherPath.isAbsolute()) {
            throw new IllegalArgumentException("'other' is different type of Path");
        }

        if (!otherPath.startsWith(this)) {
            throw new IllegalArgumentException(); // '..' is not supported, so it cannot be implemented
        }

        if (this.getNameCount() == otherPath.getNameCount()) {
            return getArchiveFileSystem().createPath(false, new String[]{""});
        }

        return otherPath.subpath(this.getNameCount(), otherPath.getNameCount());
    }

    @Override
    public URI toUri() {
        String scheme = getFileSystem().provider().getScheme();
        String archiveFile = getArchiveFileSystem().archiveFilePath.toUri().toString();
        String path = encodePath(this.toString());

        return URI.create(scheme + ":" + archiveFile + "!" + path);
    }

    @Override
    public Path toAbsolutePath() {
        if (isAbsolute) {
            return this;
        } else if (elements.length == 0) {
            return getRoot();
        } else {
            return getArchiveFileSystem().createPath(true, elements);
        }
    }

    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
        Path path = toAbsolutePath();
        if (Files.notExists(path)) {
            throw new NoSuchFileException(path.toString());
        }
        return path;
    }

    @Override
    public File toFile() {
        throw new UnsupportedOperationException("Path not associated with default file system.");
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<Path> iterator() {
        return Arrays.stream(elements)
                .map(StringArrayUtils::single)
                .map(it -> (Path) getArchiveFileSystem().createPath(false, it))
                .iterator();
    }

    @Override
    public int compareTo(Path other) {
        ArchiveFileSystemPath otherPath = checkPath(other);
        if (this.isAbsolute != otherPath.isAbsolute) {
            return isAbsolute ? 1 : -1;
        }


        String[] x = this.elements;
        String[] y = otherPath.elements;

        final int xLength = x.length;
        final int yLength = y.length;
        int length = Math.min(xLength, yLength);

        for (int i = 0; i < length; i++) {
            int v = x[i].compareTo(y[i]);
            if (v != 0) {
                return v;
            }
        }

        return Integer.signum(xLength - yLength);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof ArchiveFileSystemPath)) {
            return false;
        }

        ArchiveFileSystemPath otherPath = (ArchiveFileSystemPath) obj;
        return getFileSystem() == otherPath.getFileSystem()
                && this.isAbsolute == otherPath.isAbsolute
                && this.elements.length == otherPath.elements.length
                && this.toString().equals(obj.toString());
    }

    @Override
    public int hashCode() {
        return getFileSystem().hashCode() + toString().hashCode();
    }

    @Override
    public String toString() {
        if (display != null) {
            return display;
        }

        if (elements.length == 0) {
            return display = "/";
        }

        if (elements.length == 1 && !isAbsolute) {
            return display = elements[0];
        }

        StringBuilder builder = new StringBuilder();
        if (isAbsolute) {
            builder.append('/');
        }
        builder.append(elements[0]);

        for (int i = 1; i < elements.length; i++) {
            builder.append('/');
            builder.append(elements[i]);
        }

        return display = builder.toString();
    }
}
