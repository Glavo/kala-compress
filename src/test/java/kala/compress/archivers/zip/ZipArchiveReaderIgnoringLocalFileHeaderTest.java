/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package kala.compress.archivers.zip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Iterator;

import kala.compress.AbstractTest;
import kala.compress.utils.SeekableInMemoryByteChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class ZipArchiveReaderIgnoringLocalFileHeaderTest {

    private static ZipArchiveReader openZipWithoutLocalFileHeader(final String fileName) throws IOException {
        // @formatter:off
        return ZipArchiveReader.builder()
                .setFile(AbstractTest.getFile(fileName))
                .setCharset(StandardCharsets.UTF_8.name())
                .setUseUnicodeExtraFields(true)
                .setIgnoreLocalFileHeader(true)
                .get();
        // @formatter:on
    }

    private static ZipArchiveReader openZipWithoutLocalFileHeaderDeprecated(final String fileName) throws IOException {
        return new ZipArchiveReader(AbstractTest.getFile(fileName).toPath(), StandardCharsets.UTF_8, true, true);
    }

    @TempDir
    private File dir;

    /// Creates Unicode path and comment fields, or an empty extra field block when the prefix is absent.
    private static byte[] unicodeExtra(final String prefix, final boolean validCrc) {
        if (prefix == null) {
            return new byte[0];
        }
        return ExtraFieldUtils.mergeLocalFileDataData(new ZipExtraField[] {
                new UnicodePathExtraField(prefix + "\u00e9.txt", (validCrc ? "raw.txt" : "wrong.txt").getBytes(StandardCharsets.UTF_8)),
                new UnicodeCommentExtraField(prefix + "\u00e9 comment", (validCrc ? "raw comment" : "wrong comment").getBytes(StandardCharsets.UTF_8))
        });
    }

    /// Creates an empty stored entry with independently specified local and central extra fields.
    private static byte[] unicodeArchive(final byte[] localExtra, final byte[] centralExtra, final boolean utf8Flag) {
        final byte[] name = "raw.txt".getBytes(StandardCharsets.UTF_8);
        final byte[] comment = "raw comment".getBytes(StandardCharsets.UTF_8);
        final int centralOffset = 30 + name.length + localExtra.length;
        final int centralLength = 46 + name.length + centralExtra.length + comment.length;
        final int endOffset = centralOffset + centralLength;
        final short flags = (short) (utf8Flag ? 0x800 : 0);
        final ByteBuffer buffer = ByteBuffer.allocate(endOffset + 22).order(ByteOrder.LITTLE_ENDIAN);

        buffer.putInt(0, 0x04034b50);
        buffer.putShort(4, (short) 20);
        buffer.putShort(6, flags);
        buffer.putShort(26, (short) name.length);
        buffer.putShort(28, (short) localExtra.length);
        buffer.position(30).put(name).put(localExtra);

        buffer.putInt(centralOffset, 0x02014b50);
        buffer.putShort(centralOffset + 4, (short) 20);
        buffer.putShort(centralOffset + 6, (short) 20);
        buffer.putShort(centralOffset + 8, flags);
        buffer.putShort(centralOffset + 28, (short) name.length);
        buffer.putShort(centralOffset + 30, (short) centralExtra.length);
        buffer.putShort(centralOffset + 32, (short) comment.length);
        buffer.position(centralOffset + 46).put(name).put(centralExtra).put(comment);

        buffer.putInt(endOffset, 0x06054b50);
        buffer.putShort(endOffset + 8, (short) 1);
        buffer.putShort(endOffset + 10, (short) 1);
        buffer.putInt(endOffset + 12, centralLength);
        buffer.putInt(endOffset + 16, centralOffset);
        return buffer.array();
    }

    /// Checks Unicode resolution and name lookup with and without local header parsing.
    @ParameterizedTest
    @CsvSource({
            "central, , true, true, true, false, central, central",
            ", local, true, true, true, false, local, raw",
            "central, local, true, true, true, false, local, central",
            "central, local, true, false, true, false, raw, central",
            "central, , false, true, true, false, raw, raw",
            "central, local, false, true, true, false, local, raw",
            "central, local, true, true, false, false, raw, raw",
            "central, local, true, true, true, true, raw, raw",
            ", , true, true, true, false, raw, raw"
    })
    public void testUnicodeExtraFields(final String centralPrefix, final String localPrefix,
                                      final boolean centralCrcValid, final boolean localCrcValid,
                                      final boolean useUnicode, final boolean utf8Flag,
                                      final String expectedWithLocal, final String expectedWithoutLocal) throws IOException {
        final byte[] data = unicodeArchive(unicodeExtra(localPrefix, localCrcValid), unicodeExtra(centralPrefix, centralCrcValid), utf8Flag);
        for (final boolean ignoreLocal : new boolean[] {false, true}) {
            final String expected = ignoreLocal ? expectedWithoutLocal : expectedWithLocal;
            final boolean unicode = !"raw".equals(expected);
            final String expectedName = unicode ? expected + "\u00e9.txt" : "raw.txt";
            try (ZipArchiveReader reader = ZipArchiveReader.builder()
                    .setSeekableByteChannel(new SeekableInMemoryByteChannel(data))
                    .setCharset(StandardCharsets.UTF_8)
                    .setUseUnicodeExtraFields(useUnicode)
                    .setIgnoreLocalFileHeader(ignoreLocal)
                    .get()) {
                final ZipArchiveEntry entry = reader.getEntry(expectedName);
                assertNotNull(entry, "ignoreLocalFileHeader=" + ignoreLocal);
                assertEquals(unicode ? expected + "\u00e9 comment" : "raw comment", entry.getComment());
                assertEquals(unicode ? ZipArchiveEntry.NameSource.UNICODE_EXTRA_FIELD
                        : utf8Flag ? ZipArchiveEntry.NameSource.NAME_WITH_EFS_FLAG : ZipArchiveEntry.NameSource.NAME, entry.getNameSource());
                assertEquals(unicode ? ZipArchiveEntry.CommentSource.UNICODE_EXTRA_FIELD : ZipArchiveEntry.CommentSource.COMMENT, entry.getCommentSource());
                try (InputStream stream = reader.getInputStream(entry)) {
                    assertEquals(-1, stream.read());
                }
            }
        }
    }

    @Test
    public void testDuplicateEntry() throws Exception {
        try (ZipArchiveReader zf = openZipWithoutLocalFileHeader("COMPRESS-227.zip")) {
            int numberOfEntries = 0;
            for (final ZipArchiveEntry entry : zf.getEntries("test1.txt")) {
                numberOfEntries++;
                try (InputStream inputStream = zf.getInputStream(entry)) {
                    assertNotNull(inputStream);
                }
            }
            assertEquals(2, numberOfEntries);
        }
    }

    @Test
    public void testGetEntryWorks() throws IOException {
        try (ZipArchiveReader zf = openZipWithoutLocalFileHeader("bla.zip")) {
            final ZipArchiveEntry ze = zf.getEntry("test1.xml");
            assertEquals(610, ze.getSize());
        }
    }

    @Test
    public void testGetRawInputStreamReturnsNotNull() throws IOException {
        try (ZipArchiveReader zf = openZipWithoutLocalFileHeader("bla.zip")) {
            final ZipArchiveEntry ze = zf.getEntry("test1.xml");
            try (InputStream rawInputStream = zf.getRawInputStream(ze)) {
                assertNotNull(rawInputStream);
            }
        }
    }

    @Test
    public void testPhysicalOrder() throws IOException {
        try (ZipArchiveReader zf = openZipWithoutLocalFileHeader("ordertest.zip")) {
            final Iterator<ZipArchiveEntry> e = zf.getEntriesInPhysicalOrder().iterator();
            ZipArchiveEntry ze;
            do {
                ze = e.next();
            } while (e.hasNext());
            assertEquals("src/main/java/org/apache/commons/compress/archivers/zip/ZipUtil.java", ze.getName());
        }
    }

    /**
     * Simple unarchive test. Asserts nothing.
     *
     * @throws Exception
     */
    @Test
    public void testZipUnarchive() throws Exception {
        try (ZipArchiveReader zf = openZipWithoutLocalFileHeaderDeprecated("bla.zip")) {
            for (ZipArchiveEntry entry : zf.getEntries()) {
                try (InputStream inputStream = zf.getInputStream(entry)) {
                    Files.copy(inputStream, new File(dir, entry.getName()).toPath());
                }
            }
        }
    }
}
