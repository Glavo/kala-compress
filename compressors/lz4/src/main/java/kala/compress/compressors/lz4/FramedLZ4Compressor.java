/*
 * Copyright 2024 Glavo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kala.compress.compressors.lz4;

import kala.compress.compressors.CompressorException;
import kala.compress.compressors.CompressorInputStream;
import kala.compress.compressors.CompressorOutputStream;
import kala.compress.compressors.CompressorStreamFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Glavo
 * @since 1.21.0.1
 */
final class FramedLZ4Compressor extends CompressorStreamFactory.BuiltinCompressor {
    public FramedLZ4Compressor() {
        super(CompressorStreamFactory.LZ4_FRAMED);
    }

    @Override
    public boolean matches(byte[] signature, int length) {
        return FramedLZ4CompressorInputStream.matches(signature, length);
    }

    @Override
    public boolean isOutputAvailable() {
        return true;
    }

    @Override
    protected CompressorInputStream createCompressorInputStreamImpl(InputStream in, boolean decompressUntilEOF, int memoryLimitInKb) throws IOException, CompressorException {
        return new FramedLZ4CompressorInputStream(in, decompressUntilEOF);
    }

    @Override
    protected CompressorOutputStream<?> createCompressorOutputImpl(OutputStream out) throws IOException {
        return new FramedLZ4CompressorOutputStream(out);
    }
}
