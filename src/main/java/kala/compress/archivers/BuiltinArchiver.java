package kala.compress.archivers;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * @since 1.21.0.1
 */
public abstract class BuiltinArchiver {
    private final String name;
    private final boolean isOutputAvailable;

    protected BuiltinArchiver(String name) {
        this.name = name;
        this.isOutputAvailable = true;
    }

    protected BuiltinArchiver(String name, boolean isOutputAvailable) {
        this.name = name;
        this.isOutputAvailable = isOutputAvailable;
    }

    public final String getName() {
        return name;
    }

    public boolean matches(final byte[] signature, final int length) {
        return false;
    }

    // For verify tar file
    // COMPRESS-191 - verify the header checksum
    public boolean checkChecksum(ArchiveInputStream input) {
        return false;
    }

    public boolean isOutputAvailable() {
        return isOutputAvailable;
    }

    public ArchiveInputStream createArchiveInputStream(final InputStream in) throws ArchiveException {
        return createArchiveInputStream(in, StandardCharsets.UTF_8);
    }

    public ArchiveInputStream createArchiveInputStream(final InputStream in, final Charset charset) throws ArchiveException {
        throw new StreamingNotSupportedException(name);
    }

    public ArchiveOutputStream createArchiveOutputStream(final OutputStream out) throws ArchiveException {
        return createArchiveOutputStream(out, StandardCharsets.UTF_8);
    }

    public ArchiveOutputStream createArchiveOutputStream(final OutputStream out, final Charset charset) throws ArchiveException {
        throw new StreamingNotSupportedException(name);
    }

    @Override
    public String toString() {
        return name + " archiver";
    }
}
