package kala.compress.filesystems;

import kala.compress.filesystems.utils.glob.GlobPattern;
import kala.compress.filesystems.utils.glob.MatchingEngine;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.regex.Pattern;

public abstract class ArchiveFileSystem extends FileSystem {
    protected final Path archiveFilePath;

    protected final ArchiveFileSystemPath root = createPath(true, new String[0]);

    protected ArchiveFileSystem(Path archiveFilePath) {
        this.archiveFilePath = archiveFilePath;
    }

    protected abstract ArchiveFileSystemPath createPath(boolean isAbsolute, String[] elements);

    protected abstract ArchiveFileSystemProvider getArchiveFileSystemProvider();

    protected ArchiveFileSystemPath getRoot() {
        return root;
    }

    protected void verifyPathElement(String name) throws InvalidPathException {
        // do nothing
    }

    @Override
    public FileSystemProvider provider() {
        return getArchiveFileSystemProvider();
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public String getSeparator() {
        return "/";
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return Collections.singletonList(root);
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        final String GLOB_SYNTAX = "glob";
        final String REGEX_SYNTAX = "regex";

        int idx = syntaxAndPattern.indexOf(':');
        if (idx <= 0) {
            throw new IllegalArgumentException();
        }

        String input = syntaxAndPattern.substring(idx + 1);

        if (idx == GLOB_SYNTAX.length() && syntaxAndPattern.startsWith(GLOB_SYNTAX)) {
            final MatchingEngine pattern = GlobPattern.compile(input);
            return path -> pattern.matches(path.toString());
        } else if (idx == REGEX_SYNTAX.length() && syntaxAndPattern.startsWith(REGEX_SYNTAX)) {
            final Pattern pattern = Pattern.compile(input);
            return path -> pattern.matcher(path.toString()).matches();
        } else {
            throw new UnsupportedOperationException("Syntax '" + syntaxAndPattern.substring(0, idx) + "' not recognized");
        }
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchService newWatchService() throws IOException {
        throw new UnsupportedOperationException();
    }
}
