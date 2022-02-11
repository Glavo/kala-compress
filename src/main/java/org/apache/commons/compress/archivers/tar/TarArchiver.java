package org.apache.commons.compress.archivers.tar;

import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.BuiltinArchiver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

public class TarArchiver extends BuiltinArchiver {
    public TarArchiver() {
        super(ArchiveStreamFactory.TAR);
    }

    @Override
    public boolean matches(byte[] signature, int length) {
        return TarArchiveInputStream.matches(signature, length);
    }

    @Override
    public boolean checkChecksum(ArchiveInputStream input) {
        try {
            return input instanceof TarArchiveInputStream && ((TarArchiveInputStream) input).getNextTarEntry().isCheckSumOK();
        } catch (IOException ignored) {
            return false;
        }
    }

    @Override
    public ArchiveInputStream createArchiveInputStream(InputStream in, Charset charset) {
        return new TarArchiveInputStream(in, charset);
    }

    @Override
    public ArchiveOutputStream createArchiveOutputStream(OutputStream out, Charset charset) {
        return new TarArchiveOutputStream(out, charset);
    }
}
