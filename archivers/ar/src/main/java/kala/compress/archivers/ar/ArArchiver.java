package kala.compress.archivers.ar;

import kala.compress.archivers.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

public class ArArchiver extends BuiltinArchiver {
    public ArArchiver() {
        super(ArchiveStreamFactory.AR);
    }

    @Override
    public boolean matches(byte[] signature, int length) {
        return ArArchiveInputStream.matches(signature, length);
    }

    @Override
    public ArchiveInputStream createArchiveInputStream(InputStream in) throws ArchiveException {
        return new ArArchiveInputStream(in); // Does not support specifying charset
    }

    @Override
    public ArchiveInputStream createArchiveInputStream(InputStream in, Charset charset) throws ArchiveException {
        return new ArArchiveInputStream(in); // Does not support specifying charset
    }

    @Override
    public ArchiveOutputStream createArchiveOutputStream(OutputStream out) throws ArchiveException {
        return new ArArchiveOutputStream(out); // Does not support specifying charset
    }

    @Override
    public ArchiveOutputStream createArchiveOutputStream(OutputStream out, Charset charset) throws ArchiveException {
        return new ArArchiveOutputStream(out); // Does not support specifying charset
    }
}
