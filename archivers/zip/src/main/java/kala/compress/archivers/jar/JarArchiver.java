package kala.compress.archivers.jar;

import kala.compress.archivers.ArchiveInputStream;
import kala.compress.archivers.ArchiveOutputStream;
import kala.compress.archivers.ArchiveStreamFactory;
import kala.compress.archivers.BuiltinArchiver;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

final class JarArchiver extends BuiltinArchiver {
    public JarArchiver() {
        super(ArchiveStreamFactory.JAR);
    }

    @Override
    public boolean matches(byte[] signature, int length) {
        // return JarArchiveInputStream.matches(signature, length);
        return false; // We want to detect zip rather than jar
    }

    @Override
    public ArchiveInputStream createArchiveInputStream(InputStream in, Charset charset) {
        return new JarArchiveInputStream(in, charset);
    }

    @Override
    public ArchiveOutputStream createArchiveOutputStream(OutputStream out, Charset charset) {
        return new JarArchiveOutputStream(out, charset);
    }
}
