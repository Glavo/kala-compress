package kala.compress.compressors.z;

import kala.compress.compressors.*;

import java.io.IOException;
import java.io.InputStream;

final class ZCompressor extends BuiltinCompressor {
    public ZCompressor() {
        super(CompressorStreamFactory.Z, false);
    }

    @Override
    public boolean matches(byte[] signature, int length) {
        return  ZCompressorInputStream.matches(signature, length);
    }

    @Override
    protected CompressorInputStream internalCreateCompressorInputStream(InputStream in, boolean decompressUntilEOF, int memoryLimitInKb) throws IOException, CompressorException {
        return new ZCompressorInputStream(in, memoryLimitInKb);
    }
}
