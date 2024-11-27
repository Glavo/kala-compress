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

package org.apache.commons.compress.archivers.zip;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.zip.CRC32;

import org.apache.commons.compress.AbstractTest;
import org.apache.commons.compress.utils.Charsets;
import org.junit.jupiter.api.Test;

public class UTF8ZipFilesTest extends AbstractTest {

    private static final Charset CP437 = Charset.forName("cp437");
    private static final String ASCII_TXT = "ascii.txt";
    private static final String EURO_FOR_DOLLAR_TXT = "\u20AC_for_Dollar.txt";
    private static final String OIL_BARREL_TXT = "\u00D6lf\u00E4sser.txt";

    private static void assertRawNameOfAcsiiTxt(final ZipArchiveEntry ze) {
        final byte[] b = ze.getRawName();
        assertNotNull(b);
        final int len = ASCII_TXT.length();
        assertEquals(len, b.length);
        for (int i = 0; i < len; i++) {
            assertEquals((byte) ASCII_TXT.charAt(i), b[i], "Byte " + i);
        }
        assertNotSame(b, ze.getRawName());
    }

    private static void assertUnicodeName(final ZipArchiveEntry ze, final String expectedName, final Charset encoding) throws IOException {
        if (!expectedName.equals(ze.getName())) {
            final UnicodePathExtraField ucpf = findUniCodePath(ze);
            assertNotNull(ucpf);

            final ByteBuffer ne = Charsets.encode(encoding, ze.getName());

            final CRC32 crc = new CRC32();
            crc.update(ne.array(), ne.arrayOffset(), ne.limit() - ne.position());

            assertEquals(crc.getValue(), ucpf.getNameCRC32());
            assertEquals(expectedName, new String(ucpf.getUnicodeName(), UTF_8));
        }
    }

    private static void createTestFile(final File file, final Charset encoding, final boolean withEFS, final boolean withExplicitUnicodeExtra)
            throws IOException {

        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(file.toPath())) {
            zos.setEncoding(encoding);
            zos.setUseLanguageEncodingFlag(withEFS);
            zos.setCreateUnicodeExtraFields(
                    withExplicitUnicodeExtra ? ZipArchiveOutputStream.UnicodeExtraFieldPolicy.NEVER : ZipArchiveOutputStream.UnicodeExtraFieldPolicy.ALWAYS);

            ZipArchiveEntry ze = new ZipArchiveEntry(OIL_BARREL_TXT);
            if (withExplicitUnicodeExtra && !Charsets.canEncode(encoding, ze.getName())) {

                final ByteBuffer en = Charsets.encode(encoding, ze.getName());

                ze.addExtraField(new UnicodePathExtraField(ze.getName(), en.array(), en.arrayOffset(), en.limit() - en.position()));
            }

            zos.putArchiveEntry(ze);
            zos.write("Hello, world!".getBytes(US_ASCII));
            zos.closeArchiveEntry();

            ze = new ZipArchiveEntry(EURO_FOR_DOLLAR_TXT);
            if (withExplicitUnicodeExtra && !Charsets.canEncode(encoding, ze.getName())) {

                final ByteBuffer en = Charsets.encode(encoding, ze.getName());

                ze.addExtraField(new UnicodePathExtraField(ze.getName(), en.array(), en.arrayOffset(), en.limit() - en.position()));
            }

            zos.putArchiveEntry(ze);
            zos.write("Give me your money!".getBytes(US_ASCII));
            zos.closeArchiveEntry();

            ze = new ZipArchiveEntry(ASCII_TXT);

            if (withExplicitUnicodeExtra && !Charsets.canEncode(encoding, ze.getName())) {

                final ByteBuffer en = Charsets.encode(encoding, ze.getName());

                ze.addExtraField(new UnicodePathExtraField(ze.getName(), en.array(), en.arrayOffset(), en.limit() - en.position()));
            }

            zos.putArchiveEntry(ze);
            zos.write("ascii".getBytes(US_ASCII));
            zos.closeArchiveEntry();

            zos.finish();
        }
    }

    private static UnicodePathExtraField findUniCodePath(final ZipArchiveEntry ze) {
        return (UnicodePathExtraField) ze.getExtraField(UnicodePathExtraField.UPATH_ID);
    }

    private static void testFile(final File file, final Charset encoding) throws IOException {
        try (ZipArchiveReader zf = ZipArchiveReader.builder().setFile(file).setCharset(encoding).setUseUnicodeExtraFields(false).get()) {
            final Enumeration<ZipArchiveEntry> e = zf.getEntries();
            while (e.hasMoreElements()) {
                final ZipArchiveEntry ze = e.nextElement();
                if (ze.getName().endsWith("sser.txt")) {
                    assertUnicodeName(ze, OIL_BARREL_TXT, encoding);
                } else if (ze.getName().endsWith("_for_Dollar.txt")) {
                    assertUnicodeName(ze, EURO_FOR_DOLLAR_TXT, encoding);
                } else if (!ze.getName().equals(ASCII_TXT)) {
                    fail("Unrecognized ZIP entry with name [" + ze.getName() + "] found.");
                }
            }
        }
    }

    private void assertCanRead(final ZipArchiveReader zf, final String fileName) throws IOException {
        final ZipArchiveEntry entry = zf.getEntry(fileName);
        assertNotNull(entry, "Entry doesn't exist");
        try (InputStream is = zf.getInputStream(entry)) {
            assertNotNull(is, "InputStream is null");
            is.read();
        }
    }

    @Test
    public void testASCIIFileRoundtripExplicitUnicodeExtra() throws IOException {
        testFileRoundtrip(StandardCharsets.US_ASCII, false, true);
    }

    @Test
    public void testASCIIFileRoundtripImplicitUnicodeExtra() throws IOException {
        testFileRoundtrip(StandardCharsets.US_ASCII, false, false);
    }

    @Test
    public void testCP437FileRoundtripExplicitUnicodeExtra() throws IOException {
        testFileRoundtrip(CP437, false, true);
    }

    @Test
    public void testCP437FileRoundtripImplicitUnicodeExtra() throws IOException {
        testFileRoundtrip(CP437, false, false);
    }

    private void testFileRoundtrip(final Charset encoding, final boolean withEFS, final boolean withExplicitUnicodeExtra) throws IOException {
        final File file = createTempFile(encoding + "-test", ".zip");
        createTestFile(file, encoding, withEFS, withExplicitUnicodeExtra);
        testFile(file, encoding);
    }

    @Test
    public void testRawNameReadFromStream() throws IOException {
        try (ZipArchiveInputStream zi = new ZipArchiveInputStream(newInputStream("utf8-7zip-test.zip"), CP437, false)) {
            assertRawNameOfAcsiiTxt(zi.getNextEntry());
        }
    }

    @Test
    public void testRawNameReadFromZipFile() throws IOException {
        final File archive = getFile("utf8-7zip-test.zip");
        try (ZipArchiveReader zf = ZipArchiveReader.builder().setFile(archive).setCharset(CP437).setUseUnicodeExtraFields(false).get()) {
            assertRawNameOfAcsiiTxt(zf.getEntry(ASCII_TXT));
        }
    }

    /*
     * 7-ZIP created archive, uses EFS to signal UTF-8 file names.
     *
     * 7-ZIP doesn't use EFS for strings that can be encoded in CP437 - which is true for OIL_BARREL_TXT.
     */
    @Test
    public void testRead7ZipArchive() throws IOException {
        final File archive = getFile("utf8-7zip-test.zip");
        try (ZipArchiveReader zf = new ZipArchiveReader(archive.toPath(), CP437, false)) {
            assertNotNull(zf.getEntry(ASCII_TXT));
            assertNotNull(zf.getEntry(EURO_FOR_DOLLAR_TXT));
            assertNotNull(zf.getEntry(OIL_BARREL_TXT));
        }
    }

    @Test
    public void testRead7ZipArchiveForStream() throws IOException {
        try (ZipArchiveInputStream zi = new ZipArchiveInputStream(newInputStream("utf8-7zip-test.zip"), CP437, false)) {
            assertEquals(ASCII_TXT, zi.getNextEntry().getName());
            assertEquals(OIL_BARREL_TXT, zi.getNextEntry().getName());
            assertEquals(EURO_FOR_DOLLAR_TXT, zi.getNextEntry().getName());
        }
    }

    /*
     * WinZIP created archive, uses Unicode Extra Fields but only in the central directory.
     */
    @Test
    public void testReadWinZipArchive() throws IOException {
        final File archive = getFile("utf8-winzip-test.zip");
        try (ZipArchiveReader zf = ZipArchiveReader.builder().setFile(archive).setUseUnicodeExtraFields(true).get()) {
            assertCanRead(zf, ASCII_TXT);
            assertCanRead(zf, EURO_FOR_DOLLAR_TXT);
            assertCanRead(zf, OIL_BARREL_TXT);
        }
    }

    @Test
    public void testReadWinZipArchiveForStream() throws IOException {
        try (InputStream archive = newInputStream("utf8-winzip-test.zip");
                ZipArchiveInputStream zi = new ZipArchiveInputStream(archive, UTF_8, true)) {
            assertEquals(EURO_FOR_DOLLAR_TXT, zi.getNextEntry().getName());
            assertEquals(OIL_BARREL_TXT, zi.getNextEntry().getName());
            assertEquals(ASCII_TXT, zi.getNextEntry().getName());
        }
    }

    /**
     * @see <a href="https://issues.apache.org/jira/browse/COMPRESS-479">COMPRESS-479</a>
     */
    @Test
    public void testStreamSkipsOverUnicodeExtraFieldWithUnsupportedVersion() throws IOException {
        try (InputStream archive = newInputStream("COMPRESS-479.zip");
                ZipArchiveInputStream zi = new ZipArchiveInputStream(archive)) {
            assertEquals(OIL_BARREL_TXT, zi.getNextEntry().getName());
            assertEquals("%U20AC_for_Dollar.txt", zi.getNextEntry().getName());
            assertEquals(ASCII_TXT, zi.getNextEntry().getName());
        }
    }

    @Test
    public void testUtf8FileRoundtripExplicitUnicodeExtra() throws IOException {
        testFileRoundtrip(StandardCharsets.UTF_8, true, true);
    }

    @Test
    public void testUtf8FileRoundtripImplicitUnicodeExtra() throws IOException {
        testFileRoundtrip(StandardCharsets.UTF_8, true, false);
    }

    @Test
    public void testUtf8FileRoundtripNoEFSExplicitUnicodeExtra() throws IOException {
        testFileRoundtrip(StandardCharsets.UTF_8, false, true);
    }

    @Test
    public void testUtf8FileRoundtripNoEFSImplicitUnicodeExtra() throws IOException {
        testFileRoundtrip(StandardCharsets.UTF_8, false, false);
    }

    @Test
    public void testUtf8Interoperability() throws IOException {
        final File file1 = getFile("utf8-7zip-test.zip");
        final File file2 = getFile("utf8-winzip-test.zip");
        testFile(file1, CP437);
        testFile(file2, CP437);
    }

    @Test
    public void testZipArchiveInputStreamReadsUnicodeFields() throws IOException {
        final File file = createTempFile("unicode-test", ".zip");
        createTestFile(file, StandardCharsets.US_ASCII, false, true);
        try (ZipArchiveReader zf = ZipArchiveReader.builder().setFile(file).setCharset(StandardCharsets.US_ASCII).setUseUnicodeExtraFields(true).get()) {
            assertNotNull(zf.getEntry(ASCII_TXT));
            assertNotNull(zf.getEntry(EURO_FOR_DOLLAR_TXT));
            assertNotNull(zf.getEntry(OIL_BARREL_TXT));
        }
    }

    @Test
    public void testZipFileReadsUnicodeFields() throws IOException {
        final File file = createTempFile("unicode-test", ".zip");
        createTestFile(file, StandardCharsets.US_ASCII, false, true);
        try (ZipArchiveInputStream zi = new ZipArchiveInputStream(Files.newInputStream(file.toPath()), StandardCharsets.US_ASCII, true)) {
            assertEquals(OIL_BARREL_TXT, zi.getNextEntry().getName());
            assertEquals(EURO_FOR_DOLLAR_TXT, zi.getNextEntry().getName());
            assertEquals(ASCII_TXT, zi.getNextEntry().getName());
        }
    }

    /**
     * @see <a href="https://issues.apache.org/jira/browse/COMPRESS-479">COMPRESS-479</a>
     */
    @Test
    public void testZipFileSkipsOverUnicodeExtraFieldWithUnsupportedVersion() throws IOException {
        try (ZipArchiveReader zf = ZipArchiveReader.builder().setFile(getFile("COMPRESS-479.zip")).get()) {
            assertNotNull(zf.getEntry(ASCII_TXT));
            assertNotNull(zf.getEntry("%U20AC_for_Dollar.txt"));
            assertNotNull(zf.getEntry(OIL_BARREL_TXT));
        }
    }
}
