package kala.compress.compressors.gzip;

import kala.compress.compressors.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class GzipCompressor extends BuiltinCompressor {
    public GzipCompressor() {
        super(CompressorStreamFactory.GZIP);
    }

    @Override
    public boolean matches(byte[] signature, int length) {
        return GzipCompressorInputStream.matches(signature, length);
    }

    @Override
    protected CompressorInputStream internalCreateCompressorInputStream(InputStream in, boolean decompressUntilEOF, int memoryLimitInKb) throws IOException, CompressorException {
        return new GzipCompressorInputStream(in, decompressUntilEOF);
    }

    @Override
    protected CompressorOutputStream internalCreateCompressorOutputStream(OutputStream out) throws IOException, CompressorException {
        return new GzipCompressorOutputStream(out);
    }
}
