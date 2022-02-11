package kala.compress.compressors;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @since 1.21.0.1
 */
public abstract class BuiltinCompressor {
    private final String name;
    private final String unavailablePrompt;
    private final boolean isOutputAvailable;

    protected BuiltinCompressor(String name) {
        this(name, true);
    }

    protected BuiltinCompressor(String name, boolean isOutputAvailable) {
        this.name = name;
        this.unavailablePrompt = "";
        this.isOutputAvailable = isOutputAvailable;
    }

    protected BuiltinCompressor(String name, String dependencyName, String url) {
        this(name, true, dependencyName, url);
    }

    protected BuiltinCompressor(String name, boolean isOutputAvailable, String dependencyName, String url) {
        this.name = name;
        this.unavailablePrompt = " In addition to Glavo Compress you need the " + dependencyName + " library - see " + url;
        this.isOutputAvailable = isOutputAvailable;
    }

    public String getName() {
        return name;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isCompressionAvailable() {
        return true;
    }

    public final boolean isOutputAvailable() {
        return isOutputAvailable;
    }

    public boolean matches(final byte[] signature, final int length) {
        return false;
    }

    protected abstract CompressorInputStream internalCreateCompressorInputStream(InputStream in, boolean decompressUntilEOF, int memoryLimitInKb) throws IOException, CompressorException;

    protected CompressorOutputStream internalCreateCompressorOutputStream(OutputStream out) throws IOException, CompressorException {
        throw new CompressorException("Currently " + this + " does not support compression");
    }

    public CompressorInputStream createCompressorInputStream(InputStream in, boolean decompressUntilEOF, int memoryLimitInKb) {
        if (!isCompressionAvailable()) {
            throw new CompressorException(this + " is not available." + unavailablePrompt);
        }
        try {
            return internalCreateCompressorInputStream(in, decompressUntilEOF, memoryLimitInKb);
        } catch (IOException e) {
            throw new CompressorException("Could not create CompressorInputStream", e);
        }
    }

    public CompressorOutputStream createCompressorOutputStream(OutputStream out) throws CompressorException {
        if (!isCompressionAvailable()) {
            throw new CompressorException(this + " is not available." + unavailablePrompt);
        }
        try {
            return internalCreateCompressorOutputStream(out);
        } catch (IOException e) {
            throw new CompressorException("Could not create CompressorOutputStream", e);
        }
    }

    @Override
    public String toString() {
        return name + " compressor";
    }
}
