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
package org.apache.commons.compress.compressors.z;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Glavo
 * @since 1.21.0.1
 */
final class ZCompressor extends CompressorStreamFactory.BuiltinCompressor {
    public ZCompressor() {
        super(CompressorStreamFactory.Z);
    }

    @Override
    public boolean matches(byte[] signature, int length) {
        return ZCompressorInputStream.matches(signature, length);
    }

    @Override
    protected CompressorInputStream createCompressorInputStreamImpl(InputStream in, boolean decompressUntilEOF, int memoryLimitInKb) throws IOException, CompressorException {
        return new ZCompressorInputStream(in, memoryLimitInKb);
    }
}
