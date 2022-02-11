package org.apache.commons.compress.archivers.jar;

import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.BuiltinArchiver;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

public class JarArchiver extends BuiltinArchiver {
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
