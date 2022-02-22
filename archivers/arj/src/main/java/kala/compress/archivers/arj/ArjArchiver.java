package kala.compress.archivers.arj;

import kala.compress.archivers.ArchiveException;
import kala.compress.archivers.ArchiveInputStream;
import kala.compress.archivers.ArchiveStreamFactory;
import kala.compress.archivers.BuiltinArchiver;

import java.io.InputStream;
import java.nio.charset.Charset;

final class ArjArchiver extends BuiltinArchiver {
    public ArjArchiver() {
        super(ArchiveStreamFactory.ARJ, false);
    }

    @Override
    public boolean matches(byte[] signature, int length) {
        return ArjArchiveInputStream.matches(signature, length);
    }

    @Override
    public ArchiveInputStream createArchiveInputStream(InputStream in) throws ArchiveException {
        return new ArjArchiveInputStream(in); // Use CP437 by default
    }

    @Override
    public ArchiveInputStream createArchiveInputStream(InputStream in, Charset charset) throws ArchiveException {
        return new ArjArchiveInputStream(in, charset);
    }
}
