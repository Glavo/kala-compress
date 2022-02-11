package org.apache.commons.compress.compressors.deflate64;

import org.apache.commons.compress.compressors.BuiltinCompressor;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

import java.io.IOException;
import java.io.InputStream;

public class Deflate64Compressor extends BuiltinCompressor {
    public Deflate64Compressor() {
        super(CompressorStreamFactory.DEFLATE64, false);
    }

    @Override
    protected CompressorInputStream internalCreateCompressorInputStream(InputStream in, boolean decompressUntilEOF, int memoryLimitInKb) throws IOException, CompressorException {
        return new Deflate64CompressorInputStream(in);
    }
}
