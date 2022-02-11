package kala.compress.compressors.lz4;

import kala.compress.compressors.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FramedLZ4Compressor extends BuiltinCompressor {
    public FramedLZ4Compressor() {
        super(CompressorStreamFactory.LZ4_FRAMED);
    }

    @Override
    public boolean matches(byte[] signature, int length) {
        return FramedLZ4CompressorInputStream.matches(signature, length);
    }

    @Override
    protected CompressorInputStream internalCreateCompressorInputStream(InputStream in, boolean decompressUntilEOF, int memoryLimitInKb) throws IOException, CompressorException {
        return new FramedLZ4CompressorInputStream(in, decompressUntilEOF);
    }

    @Override
    protected CompressorOutputStream internalCreateCompressorOutputStream(OutputStream out) throws IOException, CompressorException {
        return new FramedLZ4CompressorOutputStream(out);
    }
}
