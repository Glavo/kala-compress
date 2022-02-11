package kala.compress.compressors.lz4;

import kala.compress.compressors.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class BlockLZ4Compressor extends BuiltinCompressor {
    public BlockLZ4Compressor() {
        super(CompressorStreamFactory.LZ4_BLOCK);
    }

    @Override
    protected CompressorInputStream internalCreateCompressorInputStream(InputStream in, boolean decompressUntilEOF, int memoryLimitInKb) throws IOException, CompressorException {
        return new BlockLZ4CompressorInputStream(in);
    }

    @Override
    protected CompressorOutputStream internalCreateCompressorOutputStream(OutputStream out) throws IOException, CompressorException {
        return new BlockLZ4CompressorOutputStream(out);
    }
}
