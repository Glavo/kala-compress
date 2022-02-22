package kala.compress.compressors.xz;

import kala.compress.compressors.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class XZCompressor extends BuiltinCompressor {
    public XZCompressor() {
        super(CompressorStreamFactory.XZ, "XZ for Java", "https://tukaani.org/xz/java.html");
    }

    @Override
    public boolean isCompressionAvailable() {
        return XZUtils.isXZCompressionAvailable();
    }

    @Override
    public boolean matches(byte[] signature, int length) {
        return XZUtils.matches(signature, length);
    }

    @Override
    protected CompressorInputStream internalCreateCompressorInputStream(InputStream in, boolean decompressUntilEOF, int memoryLimitInKb) throws IOException, CompressorException {
        return new XZCompressorInputStream(in, decompressUntilEOF, memoryLimitInKb);
    }

    @Override
    protected CompressorOutputStream internalCreateCompressorOutputStream(OutputStream out) throws IOException, CompressorException {
        return new XZCompressorOutputStream(out);
    }
}
