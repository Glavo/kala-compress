package kala.compress.compressors.zstandard;

import kala.compress.compressors.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ZstdCompressor extends BuiltinCompressor {

    public ZstdCompressor() {
        super(CompressorStreamFactory.ZSTANDARD, "Zstd JNI", "https://github.com/luben/zstd-jni");
    }

    @Override
    public boolean isCompressionAvailable() {
        return ZstdUtils.isZstdCompressionAvailable();
    }

    @Override
    public boolean matches(byte[] signature, int length) {
        return ZstdUtils.matches(signature, length);
    }

    @Override
    protected CompressorInputStream internalCreateCompressorInputStream(InputStream in, boolean decompressUntilEOF, int memoryLimitInKb) throws IOException, CompressorException {
         return new ZstdCompressorInputStream(in);
    }

    @Override
    protected CompressorOutputStream internalCreateCompressorOutputStream(OutputStream out) throws IOException, CompressorException {
        return new ZstdCompressorOutputStream(out);
    }
}
