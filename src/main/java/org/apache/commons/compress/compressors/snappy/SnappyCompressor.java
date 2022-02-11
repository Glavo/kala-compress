package org.apache.commons.compress.compressors.snappy;

import org.apache.commons.compress.compressors.BuiltinCompressor;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

import java.io.IOException;
import java.io.InputStream;

public class SnappyCompressor extends BuiltinCompressor {
    public SnappyCompressor() {
        super(CompressorStreamFactory.SNAPPY_RAW, false);
    }

    @Override
    protected CompressorInputStream internalCreateCompressorInputStream(InputStream in, boolean decompressUntilEOF, int memoryLimitInKb) throws IOException, CompressorException {
        return new SnappyCompressorInputStream(in);
    }
}
