package org.apache.commons.compress.compressors.brotli;

import org.apache.commons.compress.compressors.BuiltinCompressor;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

import java.io.IOException;
import java.io.InputStream;

public class BrotliCompressor extends BuiltinCompressor {
    public BrotliCompressor() {
        super(CompressorStreamFactory.BROTLI);
    }

    @Override
    public boolean isCompressionAvailable() {
        return BrotliUtils.isBrotliCompressionAvailable();
    }

    @Override
    protected CompressorInputStream internalCreateCompressorInputStream(InputStream in, boolean decompressUntilEOF, int memoryLimitInKb) throws IOException, CompressorException {
        return new BrotliCompressorInputStream(in);
    }
}
