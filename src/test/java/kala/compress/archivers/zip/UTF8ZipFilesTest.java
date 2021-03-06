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
 *
 */

package kala.compress.archivers.zip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.zip.CRC32;

import kala.compress.AbstractTestCase;
import kala.compress.utils.Charsets;
import org.junit.Test;

public class UTF8ZipFilesTest extends AbstractTestCase {

    private static final Charset CP437 = Charsets.toCharset("cp437");
    private static final String ASCII_TXT = "ascii.txt";
    private static final String EURO_FOR_DOLLAR_TXT = "\u20AC_for_Dollar.txt";
    private static final String OIL_BARREL_TXT = "\u00D6lf\u00E4sser.txt";

    @Test
    public void testUtf8FileRoundtripExplicitUnicodeExtra()
        throws IOException {
        testFileRoundtrip(StandardCharsets.UTF_8, true, true);
    }

    @Test
    public void testUtf8FileRoundtripNoEFSExplicitUnicodeExtra()
        throws IOException {
        testFileRoundtrip(StandardCharsets.UTF_8, false, true);
    }

    @Test
    public void testCP437FileRoundtripExplicitUnicodeExtra()
        throws IOException {
        testFileRoundtrip(CP437, false, true);
    }

    @Test
    public void testASCIIFileRoundtripExplicitUnicodeExtra()
        throws IOException {
        testFileRoundtrip(StandardCharsets.US_ASCII, false, true);
    }

    @Test
    public void testUtf8FileRoundtripImplicitUnicodeExtra()
        throws IOException {
        testFileRoundtrip(StandardCharsets.UTF_8, true, false);
    }

    @Test
    public void testUtf8FileRoundtripNoEFSImplicitUnicodeExtra()
        throws IOException {
        testFileRoundtrip(StandardCharsets.UTF_8, false, false);
    }

    @Test
    public void testCP437FileRoundtripImplicitUnicodeExtra()
        throws IOException {
        testFileRoundtrip(CP437, false, false);
    }

    @Test
    public void testASCIIFileRoundtripImplicitUnicodeExtra()
        throws IOException {
        testFileRoundtrip(StandardCharsets.US_ASCII, false, false);
    }

    /*
     * 7-ZIP created archive, uses EFS to signal UTF-8 file names.
     *
     * 7-ZIP doesn't use EFS for strings that can be encoded in CP437
     * - which is true for OIL_BARREL_TXT.
     */
    @Test
    public void testRead7ZipArchive() throws IOException {
        final File archive = getFile("utf8-7zip-test.zip");
        ZipArchiveReader zf = null;
        try {
            zf = new ZipArchiveReader(archive, Charsets.toCharset(CP437), false);
            assertNotNull(zf.getEntry(ASCII_TXT));
            assertNotNull(zf.getEntry(EURO_FOR_DOLLAR_TXT));
            assertNotNull(zf.getEntry(OIL_BARREL_TXT));
        } finally {
            ZipArchiveReader.closeQuietly(zf);
        }
    }

    @Test
    public void testRead7ZipArchiveForStream() throws IOException {
        final InputStream archive =
                Files.newInputStream(getFile("utf8-7zip-test.zip").toPath());
        try (ZipArchiveInputStream zi = new ZipArchiveInputStream(archive, Charsets.toCharset(CP437), false)) {
            assertEquals(ASCII_TXT, zi.getNextEntry().getName());
            assertEquals(OIL_BARREL_TXT, zi.getNextEntry().getName());
            assertEquals(EURO_FOR_DOLLAR_TXT, zi.getNextEntry().getName());
        }
    }

    /*
     * WinZIP created archive, uses Unicode Extra Fields but only in
     * the central directory.
     */
    @Test
    public void testReadWinZipArchive() throws IOException {
        final File archive = getFile("utf8-winzip-test.zip");
        ZipArchiveReader zf = null;
        try {
            zf = new ZipArchiveReader(archive, StandardCharsets.UTF_8, true);
            assertCanRead(zf, ASCII_TXT);
            assertCanRead(zf, EURO_FOR_DOLLAR_TXT);
            assertCanRead(zf, OIL_BARREL_TXT);
        } finally {
            ZipArchiveReader.closeQuietly(zf);
        }
    }

    private void assertCanRead(final ZipArchiveReader zf, final String fileName) throws IOException {
        final ZipArchiveEntry entry = zf.getEntry(fileName);
        assertNotNull("Entry doesn't exist", entry);
        final InputStream is = zf.getInputStream(entry);
        assertNotNull("InputStream is null", is);
        try {
            is.read();
        } finally {
            is.close();
        }
    }

    @Test
    public void testReadWinZipArchiveForStream() throws IOException {
        final InputStream archive =
            Files.newInputStream(getFile("utf8-winzip-test.zip").toPath());
        ZipArchiveInputStream zi = null;
        try {
            zi = new ZipArchiveInputStream(archive, StandardCharsets.UTF_8, true);
            assertEquals(EURO_FOR_DOLLAR_TXT, zi.getNextEntry().getName());
            assertEquals(OIL_BARREL_TXT, zi.getNextEntry().getName());
            assertEquals(ASCII_TXT, zi.getNextEntry().getName());
        } finally {
            if (zi != null) {
                zi.close();
            }
        }
    }

    @Test
    public void testZipFileReadsUnicodeFields() throws IOException {
        final File file = File.createTempFile("unicode-test", ".zip");
        file.deleteOnExit();
        ZipArchiveInputStream zi = null;
        try {
            createTestFile(file, StandardCharsets.US_ASCII, false, true);
            final InputStream archive = Files.newInputStream(file.toPath());
            zi = new ZipArchiveInputStream(archive, StandardCharsets.US_ASCII, true);
            assertEquals(OIL_BARREL_TXT, zi.getNextEntry().getName());
            assertEquals(EURO_FOR_DOLLAR_TXT, zi.getNextEntry().getName());
            assertEquals(ASCII_TXT, zi.getNextEntry().getName());
        } finally {
            if (zi != null) {
                zi.close();
            }
            tryHardToDelete(file);
        }
    }

    @Test
    public void testZipArchiveInputStreamReadsUnicodeFields()
        throws IOException {
        final File file = File.createTempFile("unicode-test", ".zip");
        file.deleteOnExit();
        ZipArchiveReader zf = null;
        try {
            createTestFile(file, StandardCharsets.US_ASCII, false, true);
            zf = new ZipArchiveReader(file, StandardCharsets.US_ASCII, true);
            assertNotNull(zf.getEntry(ASCII_TXT));
            assertNotNull(zf.getEntry(EURO_FOR_DOLLAR_TXT));
            assertNotNull(zf.getEntry(OIL_BARREL_TXT));
        } finally {
            ZipArchiveReader.closeQuietly(zf);
            tryHardToDelete(file);
        }
    }

    @Test
    public void testRawNameReadFromZipFile()
        throws IOException {
        final File archive = getFile("utf8-7zip-test.zip");
        ZipArchiveReader zf = null;
        try {
            zf = new ZipArchiveReader(archive, Charsets.toCharset(CP437), false);
            assertRawNameOfAcsiiTxt(zf.getEntry(ASCII_TXT));
        } finally {
            ZipArchiveReader.closeQuietly(zf);
        }
    }

    @Test
    public void testRawNameReadFromStream()
        throws IOException {
        final InputStream archive =
            Files.newInputStream(getFile("utf8-7zip-test.zip").toPath());
        try (ZipArchiveInputStream zi = new ZipArchiveInputStream(archive, Charsets.toCharset(CP437), false)) {
            assertRawNameOfAcsiiTxt((ZipArchiveEntry) zi.getNextEntry());
        }
    }

    /**
     * @see <a href="https://issues.apache.org/jira/browse/COMPRESS-479">COMPRESS-479</a>
     */
    @Test
    public void streamSkipsOverUnicodeExtraFieldWithUnsupportedVersion() throws IOException {
        try (InputStream archive = Files.newInputStream(getFile("COMPRESS-479.zip").toPath());
             ZipArchiveInputStream zi = new ZipArchiveInputStream(archive)) {
            assertEquals(OIL_BARREL_TXT, zi.getNextEntry().getName());
            assertEquals("%U20AC_for_Dollar.txt", zi.getNextEntry().getName());
            assertEquals(ASCII_TXT, zi.getNextEntry().getName());
        }
    }

    /**
     * @see <a href="https://issues.apache.org/jira/browse/COMPRESS-479">COMPRESS-479</a>
     */
    @Test
    public void zipFileSkipsOverUnicodeExtraFieldWithUnsupportedVersion() throws IOException {
        try (ZipArchiveReader zf = new ZipArchiveReader(getFile("COMPRESS-479.zip"))) {
            assertNotNull(zf.getEntry(ASCII_TXT));
            assertNotNull(zf.getEntry("%U20AC_for_Dollar.txt"));
            assertNotNull(zf.getEntry(OIL_BARREL_TXT));
        }
    }

    private static void testFileRoundtrip(final Charset charset, final boolean withEFS,
                                          final boolean withExplicitUnicodeExtra)
        throws IOException {

        final File file = File.createTempFile(charset.name() + "-test", ".zip");
        file.deleteOnExit();
        try {
            createTestFile(file, charset, withEFS, withExplicitUnicodeExtra);
            testFile(file, charset);
        } finally {
            tryHardToDelete(file);
        }
    }

    private static void createTestFile(final File file, final Charset charset,
                                       final boolean withEFS,
                                       final boolean withExplicitUnicodeExtra)
        throws IOException {

        ZipArchiveOutputStream zos = null;
        try {
            zos = new ZipArchiveOutputStream(file);
            zos.setCharset(charset);
            zos.setUseLanguageEncodingFlag(withEFS);
            zos.setCreateUnicodeExtraFields(withExplicitUnicodeExtra ?
                                            ZipArchiveOutputStream
                                            .UnicodeExtraFieldPolicy.NEVER
                                            : ZipArchiveOutputStream
                                            .UnicodeExtraFieldPolicy.ALWAYS);

            ZipArchiveEntry ze = new ZipArchiveEntry(OIL_BARREL_TXT);
            if (withExplicitUnicodeExtra
                && !Charsets.canEncode(charset, ze.getName())) {

                final ByteBuffer en = Charsets.encode(charset, ze.getName());

                ze.addExtraField(new UnicodePathExtraField(ze.getName(),
                                                           en.array(),
                                                           en.arrayOffset(),
                                                           en.limit()
                                                           - en.position()));
            }

            zos.putArchiveEntry(ze);
            zos.write("Hello, world!".getBytes(StandardCharsets.US_ASCII));
            zos.closeArchiveEntry();

            ze = new ZipArchiveEntry(EURO_FOR_DOLLAR_TXT);
            if (withExplicitUnicodeExtra
                && !Charsets.canEncode(charset, ze.getName())) {

                final ByteBuffer en = Charsets.encode(charset, ze.getName());

                ze.addExtraField(new UnicodePathExtraField(ze.getName(),
                                                           en.array(),
                                                           en.arrayOffset(),
                                                           en.limit()
                                                           - en.position()));
            }

            zos.putArchiveEntry(ze);
            zos.write("Give me your money!".getBytes(StandardCharsets.US_ASCII));
            zos.closeArchiveEntry();

            ze = new ZipArchiveEntry(ASCII_TXT);

            if (withExplicitUnicodeExtra
                && !Charsets.canEncode(charset, ze.getName())) {

                final ByteBuffer en = Charsets.encode(charset, ze.getName());

                ze.addExtraField(new UnicodePathExtraField(ze.getName(),
                                                           en.array(),
                                                           en.arrayOffset(),
                                                           en.limit()
                                                           - en.position()));
            }

            zos.putArchiveEntry(ze);
            zos.write("ascii".getBytes(StandardCharsets.US_ASCII));
            zos.closeArchiveEntry();

            zos.finish();
        } finally {
            if (zos != null) {
                try {
                    zos.close();
                } catch (final IOException e) { /* swallow */ }
            }
        }
    }

    private static void testFile(final File file, final Charset charset)
        throws IOException {
        ZipArchiveReader zf = null;
        try {
            zf = new ZipArchiveReader(file, charset, false);

            final Enumeration<ZipArchiveEntry> e = zf.getEntries();
            while (e.hasMoreElements()) {
                final ZipArchiveEntry ze = e.nextElement();

                if (ze.getName().endsWith("sser.txt")) {
                    assertUnicodeName(ze, OIL_BARREL_TXT, charset);

                } else if (ze.getName().endsWith("_for_Dollar.txt")) {
                    assertUnicodeName(ze, EURO_FOR_DOLLAR_TXT, charset);
                } else if (!ze.getName().equals(ASCII_TXT)) {
                    throw new AssertionError("Unrecognized ZIP entry with name ["
                                             + ze.getName() + "] found.");
                }
            }
        } finally {
            ZipArchiveReader.closeQuietly(zf);
        }
    }

    private static UnicodePathExtraField findUniCodePath(final ZipArchiveEntry ze) {
        return (UnicodePathExtraField)
            ze.getExtraField(UnicodePathExtraField.UPATH_ID);
    }

    private static void assertUnicodeName(final ZipArchiveEntry ze,
                                          final String expectedName,
                                          final Charset charset)
        throws IOException {
        if (!expectedName.equals(ze.getName())) {
            final UnicodePathExtraField ucpf = findUniCodePath(ze);
            assertNotNull(ucpf);

            final ByteBuffer ne = Charsets.encode(charset, ze.getName());

            final CRC32 crc = new CRC32();
            crc.update(ne.array(), ne.arrayOffset(),
                       ne.limit() - ne.position());

            assertEquals(crc.getValue(), ucpf.getNameCRC32());
            assertEquals(expectedName, new String(ucpf.getUnicodeName(),
                    StandardCharsets.UTF_8));
        }
    }

    @Test
    public void testUtf8Interoperability() throws IOException {
        final File file1 = getFile("utf8-7zip-test.zip");
        final File file2 = getFile("utf8-winzip-test.zip");

        testFile(file1,CP437);
        testFile(file2,CP437);

    }

    private static void assertRawNameOfAcsiiTxt(final ZipArchiveEntry ze) {
        final byte[] b = ze.getRawName();
        assertNotNull(b);
        final int len = ASCII_TXT.length();
        assertEquals(len, b.length);
        for (int i = 0; i < len; i++) {
            assertEquals("Byte " + i, (byte) ASCII_TXT.charAt(i), b[i]);
        }
        assertNotSame(b, ze.getRawName());
    }
}

