package kala.compress.compressors.brotli;

import kala.compress.compressors.BuiltinCompressor;
import kala.compress.compressors.CompressorException;
import kala.compress.compressors.CompressorInputStream;
import kala.compress.compressors.CompressorStreamFactory;

import java.io.IOException;
import java.io.InputStream;

public class BrotliCompressor extends BuiltinCompressor {
    public BrotliCompressor() {
        super(CompressorStreamFactory.BROTLI);
    }

    @Override
    protected CompressorInputStream internalCreateCompressorInputStream(InputStream in, boolean decompressUntilEOF, int memoryLimitInKb) throws IOException, CompressorException {
        return new BrotliCompressorInputStream(in);
    }
}
