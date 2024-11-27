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
package org.apache.commons.compress.archivers.arj;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;

import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * @author Glavo
 * @since 1.21.0.1
 */
final class ArjArchiver extends ArchiveStreamFactory.BuiltinArchiver {
    public ArjArchiver() {
        super(ArchiveStreamFactory.ARJ);
    }

    @Override
    public boolean matches(byte[] signature, int length) {
        return ArjArchiveInputStream.matches(signature, length);
    }

    @Override
    public ArchiveInputStream<?> createArchiveInputStream(InputStream in, Charset charset) throws ArchiveException {
        return new ArjArchiveInputStream(in, charset);
    }
}
