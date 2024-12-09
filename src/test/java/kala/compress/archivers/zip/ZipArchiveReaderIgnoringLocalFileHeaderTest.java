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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Iterator;

import kala.compress.AbstractTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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