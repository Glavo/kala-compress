package kala.compress.compressors.deflate64;

import kala.compress.compressors.BuiltinCompressor;
import kala.compress.compressors.CompressorException;
import kala.compress.compressors.CompressorInputStream;
import kala.compress.compressors.CompressorStreamFactory;

import java.io.IOException;
import java.io.InputStream;

final class Deflate64Compressor extends BuiltinCompressor {
    public Deflate64Compressor() {
        super(CompressorStreamFactory.DEFLATE64, false);
    }

    @Override
    protected CompressorInputStream internalCreateCompressorInputStream(InputStream in, boolean decompressUntilEOF, int memoryLimitInKb) throws IOException, CompressorException {
        return new Deflate64CompressorInputStream(in);
    }
}
