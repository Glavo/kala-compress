package kala.compress.compressors.bzip2;

import kala.compress.compressors.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class BZip2Compressor extends BuiltinCompressor {
    public BZip2Compressor() {
        super(CompressorStreamFactory.BZIP2);
    }

    @Override
    public boolean matches(byte[] signature, int length) {
        return BZip2CompressorInputStream.matches(signature, length);
    }

    @Override
    protected CompressorInputStream internalCreateCompressorInputStream(InputStream in, boolean decompressUntilEOF, int memoryLimitInKb) throws IOException, CompressorException {
        return new BZip2CompressorInputStream(in, decompressUntilEOF);
    }

    @Override
    protected CompressorOutputStream internalCreateCompressorOutputStream(OutputStream out) throws IOException, CompressorException {
        return new BZip2CompressorOutputStream(out);
    }
}
