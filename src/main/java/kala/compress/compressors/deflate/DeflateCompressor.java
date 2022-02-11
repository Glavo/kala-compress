package kala.compress.compressors.deflate;

import kala.compress.compressors.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class DeflateCompressor extends BuiltinCompressor {
    public DeflateCompressor() {
        super(CompressorStreamFactory.DEFLATE);
    }

    @Override
    public boolean matches(byte[] signature, int length) {
        return DeflateCompressorInputStream.matches(signature, length);
    }

    @Override
    protected CompressorInputStream internalCreateCompressorInputStream(InputStream in, boolean decompressUntilEOF, int memoryLimitInKb) throws IOException, CompressorException {
        return new DeflateCompressorInputStream(in);
    }

    @Override
    protected CompressorOutputStream internalCreateCompressorOutputStream(OutputStream out) throws IOException, CompressorException {
        return new DeflateCompressorOutputStream(out);
    }
}
