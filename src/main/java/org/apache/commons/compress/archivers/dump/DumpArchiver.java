package org.apache.commons.compress.archivers.dump;

import org.apache.commons.compress.archivers.*;

import java.io.InputStream;
import java.nio.charset.Charset;

public class DumpArchiver extends BuiltinArchiver {
    public DumpArchiver() {
        super(ArchiveStreamFactory.DUMP, false);
    }

    @Override
    public boolean matches(byte[] signature, int length) {
        return DumpArchiveInputStream.matches(signature, length);
    }

    @Override
    public ArchiveInputStream createArchiveInputStream(InputStream in, Charset charset) throws ArchiveException {
        return new DumpArchiveInputStream(in, charset);
    }
}
