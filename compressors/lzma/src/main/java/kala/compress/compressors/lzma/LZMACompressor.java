package kala.compress.compressors.lzma;

import kala.compress.compressors.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class LZMACompressor extends BuiltinCompressor {
    public LZMACompressor() {
        super(CompressorStreamFactory.LZMA);
    }

    @Override
    public boolean isCompressionAvailable() {
        return LZMAUtils.isLZMACompressionAvailable();
    }

    @Override
    public boolean matches(byte[] signature, int length) {
        return LZMAUtils.matches(signature, length);
    }

    @Override
    protected CompressorInputStream internalCreateCompressorInputStream(InputStream in, boolean decompressUntilEOF, int memoryLimitInKb) throws IOException, CompressorException {
        return new LZMACompressorInputStream(in, memoryLimitInKb);
    }

    @Override
    protected CompressorOutputStream internalCreateCompressorOutputStream(OutputStream out) throws IOException, CompressorException {
        return new LZMACompressorOutputStream(out);
    }
}
