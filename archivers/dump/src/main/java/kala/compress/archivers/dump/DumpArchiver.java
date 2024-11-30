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
package kala.compress.archivers.dump;

import kala.compress.archivers.ArchiveException;
import kala.compress.archivers.ArchiveInputStream;
import kala.compress.archivers.ArchiveStreamFactory;

import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * @author Glavo
 * @since 1.21.0.1
 */
final class DumpArchiver extends ArchiveStreamFactory.BuiltinArchiver {
    public DumpArchiver() {
        super(ArchiveStreamFactory.DUMP);
    }

    @Override
    public boolean matches(byte[] signature, int length) {
        return DumpArchiveInputStream.matches(signature, length);
    }

    @Override
    public ArchiveInputStream<?> createArchiveInputStream(InputStream in, Charset charset) throws ArchiveException {
        return new DumpArchiveInputStream(in, charset);
    }
}
