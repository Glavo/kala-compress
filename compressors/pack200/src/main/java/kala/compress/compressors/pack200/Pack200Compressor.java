package kala.compress.compressors.pack200;

import kala.compress.compressors.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class Pack200Compressor extends BuiltinCompressor {
    public Pack200Compressor() {
        super(CompressorStreamFactory.PACK200, "Pack200", "https://github.com/Glavo/pack200");
    }

    @Override
    public boolean isCompressionAvailable() {
        return Pack200Utils.isPack200Available();
    }

    @Override
    public boolean matches(byte[] signature, int length) {
        return Pack200CompressorInputStream.matches(signature, length);
    }

    @Override
    protected CompressorInputStream internalCreateCompressorInputStream(InputStream in, boolean decompressUntilEOF, int memoryLimitInKb) throws IOException, CompressorException {
        return new Pack200CompressorInputStream(in);
    }

    @Override
    protected CompressorOutputStream internalCreateCompressorOutputStream(OutputStream out) throws IOException, CompressorException {
        return new Pack200CompressorOutputStream(out);
    }
}
