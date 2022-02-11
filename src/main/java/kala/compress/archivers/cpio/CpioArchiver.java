package kala.compress.archivers.cpio;

import kala.compress.archivers.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

public class CpioArchiver extends BuiltinArchiver {
    public CpioArchiver() {
        super(ArchiveStreamFactory.CPIO);
    }

    @Override
    public boolean matches(byte[] signature, int length) {
        return CpioArchiveInputStream.matches(signature, length);
    }

    @Override
    public ArchiveInputStream createArchiveInputStream(InputStream in) throws ArchiveException {
        return new CpioArchiveInputStream(in); // Use ASCII by default
    }

    @Override
    public ArchiveInputStream createArchiveInputStream(InputStream in, Charset charset) throws ArchiveException {
        return new CpioArchiveInputStream(in, charset);
    }

    @Override
    public ArchiveOutputStream createArchiveOutputStream(OutputStream out) throws ArchiveException {
        return new CpioArchiveOutputStream(out); // Use ASCII by default
    }

    @Override
    public ArchiveOutputStream createArchiveOutputStream(OutputStream out, Charset charset) throws ArchiveException {
        return new CpioArchiveOutputStream(out, charset);
    }
}
