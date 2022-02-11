package kala.compress.compressors.snappy;

import kala.compress.compressors.BuiltinCompressor;
import kala.compress.compressors.CompressorException;
import kala.compress.compressors.CompressorInputStream;
import kala.compress.compressors.CompressorStreamFactory;

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
