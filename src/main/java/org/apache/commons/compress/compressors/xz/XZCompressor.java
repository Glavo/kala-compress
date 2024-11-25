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
package org.apache.commons.compress.compressors.xz;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorOutputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Glavo
 * @since 1.21.0.1
 */
final class XZCompressor extends CompressorStreamFactory.BuiltinCompressor {
    public XZCompressor() {
        super(CompressorStreamFactory.XZ, "XZ for Java", "https://tukaani.org/xz/java.html");
    }

    @Override
    public boolean isCompressionAvailable() {
        return XZUtils.isXZCompressionAvailable();
    }

    @Override
    public boolean matches(byte[] signature, int length) {
        return XZUtils.matches(signature, length);
    }

    @Override
    public boolean isOutputAvailable() {
        return true;
    }

    @Override
    protected CompressorInputStream createCompressorInputStreamImpl(InputStream in, boolean decompressUntilEOF, int memoryLimitInKb) throws IOException, CompressorException {
        return new XZCompressorInputStream(in, decompressUntilEOF, memoryLimitInKb);
    }

    @Override
    protected CompressorOutputStream<?> createCompressorOutputImpl(OutputStream out) throws IOException {
        return new XZCompressorOutputStream(out);
    }
}