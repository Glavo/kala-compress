package kala.compress.archivers.dump;

import kala.compress.archivers.*;

import java.io.InputStream;
import java.nio.charset.Charset;

final class DumpArchiver extends BuiltinArchiver {
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
