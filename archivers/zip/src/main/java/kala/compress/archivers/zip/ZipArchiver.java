package kala.compress.archivers.zip;

import kala.compress.archivers.ArchiveInputStream;
import kala.compress.archivers.ArchiveOutputStream;
import kala.compress.archivers.ArchiveStreamFactory;
import kala.compress.archivers.BuiltinArchiver;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

final class ZipArchiver extends BuiltinArchiver {
    public ZipArchiver() {
        super(ArchiveStreamFactory.ZIP);
    }

    @Override
    public boolean matches(byte[] signature, int length) {
        return ZipArchiveInputStream.matches(signature, length);
    }

    @Override
    public ArchiveInputStream createArchiveInputStream(InputStream in, Charset charset) {
        return new ZipArchiveInputStream(in, charset);
    }

    @Override
    public ArchiveOutputStream createArchiveOutputStream(OutputStream out, Charset charset) {
        return new ZipArchiveOutputStream(out, charset);
    }
}
