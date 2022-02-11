package org.apache.commons.compress.archivers.arj;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.BuiltinArchiver;

import java.io.InputStream;
import java.nio.charset.Charset;

public class ArjArchiver extends BuiltinArchiver {
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
