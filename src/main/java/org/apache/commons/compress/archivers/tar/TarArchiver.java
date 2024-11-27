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
package org.apache.commons.compress.archivers.tar;

import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * @author Glavo
 * @since 1.21.0.1
 */
final class TarArchiver extends ArchiveStreamFactory.BuiltinArchiver {
    private static final int TAR_TEST_ENTRY_COUNT = 10;

    public TarArchiver() {
        super(ArchiveStreamFactory.TAR);
    }

    @Override
    public boolean matches(byte[] signature, int length) {
        return TarArchiveInputStream.matches(signature, length);
    }

    @Override
    public boolean checkTarChecksum(byte[] tarHeader) {
        try (TarArchiveInputStream inputStream = new TarArchiveInputStream(new ByteArrayInputStream(tarHeader))) {
            // COMPRESS-191 - verify the header checksum
            // COMPRESS-644 - do not allow zero byte file entries
            TarArchiveEntry entry = inputStream.getNextEntry();
            // try to find the first non-directory entry within the first 10 entries.
            int count = 0;
            while (entry != null && entry.isDirectory() && entry.isCheckSumOK() && count++ < TAR_TEST_ENTRY_COUNT) {
                entry = inputStream.getNextEntry();
            }
            if (entry != null && entry.isCheckSumOK() && !entry.isDirectory() && entry.getSize() > 0 || count > 0) {
                return true;
            }
        } catch (final Exception ignored) {
            // can generate IllegalArgumentException as well as IOException auto-detection, simply not a TAR ignored
        }

        return false;
    }

    @Override
    public ArchiveInputStream<?> createArchiveInputStream(InputStream in, Charset charset) {
        return new TarArchiveInputStream(in, charset);
    }

    @Override
    public boolean isOutputAvailable() {
        return true;
    }

    @Override
    public ArchiveOutputStream<?> createArchiveOutputStream(OutputStream out, Charset charset) {
        return new TarArchiveOutputStream(out, charset);
    }
}