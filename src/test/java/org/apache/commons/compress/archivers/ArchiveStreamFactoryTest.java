/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.commons.compress.archivers;

import static org.apache.commons.compress.AbstractTestCase.getFile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;

import org.apache.commons.compress.MockEvilInputStream;
import org.apache.commons.compress.archivers.arj.ArjArchiveInputStream;
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream;
import org.apache.commons.compress.archivers.dump.DumpArchiveInputStream;
import org.apache.commons.compress.archivers.jar.JarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.utils.ByteUtils;
import org.junit.Test;

public class ArchiveStreamFactoryTest {

    private static final Charset UNKNOWN = null;

    /**
     * see https://issues.apache.org/jira/browse/COMPRESS-171
     */
    @Test
    public void shortTextFilesAreNoTARs() {
        try {
            ArchiveStreamFactory.DEFAULT
                .createArchiveInputStream(new ByteArrayInputStream("This certainly is not a tar archive, really, no kidding".getBytes()));
            fail("created an input stream for a non-archive");
        } catch (final ArchiveException ae) {
            assertTrue(ae.getMessage().startsWith("No Archiver found"));
        }
    }

    /**
     * see https://issues.apache.org/jira/browse/COMPRESS-191
     */
    @Test
    public void aiffFilesAreNoTARs() throws Exception {
        try (InputStream fis = Files.newInputStream(new File("src/test/resources/testAIFF.aif").toPath())) {
            try (InputStream is = new BufferedInputStream(fis)) {
                ArchiveStreamFactory.DEFAULT.createArchiveInputStream(is);
                fail("created an input stream for a non-archive");
            } catch (final ArchiveException ae) {
                assertTrue(ae.getMessage().startsWith("No Archiver found"));
            }
        }
    }

    @Test
    public void testCOMPRESS209() throws Exception {
        try (InputStream fis = Files.newInputStream(new File("src/test/resources/testCompress209.doc").toPath())) {
            try (InputStream bis = new BufferedInputStream(fis)) {
                ArchiveStreamFactory.DEFAULT.createArchiveInputStream(bis);
                fail("created an input stream for a non-archive");
            } catch (final ArchiveException ae) {
                assertTrue(ae.getMessage().startsWith("No Archiver found"));
            }
        }
    }

    @Test(expected = StreamingNotSupportedException.class)
    public void cantRead7zFromStream() throws Exception {
        ArchiveStreamFactory.DEFAULT
            .createArchiveInputStream(ArchiveStreamFactory.SEVEN_Z,
                                      new ByteArrayInputStream(ByteUtils.EMPTY_BYTE_ARRAY));
    }

    @Test(expected = StreamingNotSupportedException.class)
    public void cantWrite7zToStream() throws Exception {
        ArchiveStreamFactory.DEFAULT
            .createArchiveOutputStream(ArchiveStreamFactory.SEVEN_Z,
                                       new ByteArrayOutputStream());
    }

    /**
     * Test case for
     * <a href="https://issues.apache.org/jira/browse/COMPRESS-267"
     * >COMPRESS-267</a>.
     */
    @Test
    public void detectsAndThrowsFor7z() throws Exception {
        try (InputStream fis = Files.newInputStream(new File("src/test/resources/bla.7z").toPath())) {
            try (InputStream bis = new BufferedInputStream(fis)) {
                ArchiveStreamFactory.DEFAULT.createArchiveInputStream(bis);
                fail("Expected a StreamingNotSupportedException");
            } catch (final StreamingNotSupportedException ex) {
                assertEquals(ArchiveStreamFactory.SEVEN_Z, ex.getFormat());
            }
        }
    }

    /**
     * Test case for
     * <a href="https://issues.apache.org/jira/browse/COMPRESS-208"
     * >COMPRESS-208</a>.
     */
    @Test
    public void skipsPK00Prefix() throws Exception {
        try (InputStream fis = Files.newInputStream(new File("src/test/resources/COMPRESS-208.zip").toPath())) {
            try (InputStream bis = new BufferedInputStream(fis)) {
                try (ArchiveInputStream ais = ArchiveStreamFactory.DEFAULT.createArchiveInputStream(bis)) {
                    assertTrue(ais instanceof ZipArchiveInputStream);
                }
            }
        }
    }

    @Test
    public void testEncodingCtor() {
        ArchiveStreamFactory fac = new ArchiveStreamFactory();
        assertNull(fac.getEntryCharset());
        fac = new ArchiveStreamFactory((Charset) null);
        assertNull(fac.getEntryCharset());
        fac = new ArchiveStreamFactory("UTF-8");
        assertEquals(StandardCharsets.UTF_8, fac.getEntryCharset());
        fac = new ArchiveStreamFactory(StandardCharsets.UTF_8);
        assertEquals(StandardCharsets.UTF_8, fac.getEntryCharset());
    }

    static class TestData {
        final String testFile;
        final Charset expectedCharset;
        final ArchiveStreamFactory fac;
        final String fieldName;
        final String type;
        final boolean hasOutputStream;

        TestData(final String testFile, final String type, final boolean hasOut, final Charset expectedCharset, final ArchiveStreamFactory fac, final String fieldName) {
            this.testFile = testFile;
            this.expectedCharset = expectedCharset;
            this.fac = fac;
            this.fieldName = fieldName;
            this.type = type;
            this.hasOutputStream = hasOut;
        }

        @Override
        public String toString() {
            return "TestData [testFile=" + testFile + ", expectedCharset=" + expectedCharset + ", fac=" + fac
                    + ", fieldName=" + fieldName + ", type=" + type + ", hasOutputStream=" + hasOutputStream + "]";
        }
    }

    // The different factory types
    private static final ArchiveStreamFactory FACTORY = ArchiveStreamFactory.DEFAULT;
    private static final ArchiveStreamFactory FACTORY_UTF8 = new ArchiveStreamFactory("UTF-8");
    private static final ArchiveStreamFactory FACTORY_ASCII = new ArchiveStreamFactory("ASCII");

    // Default encoding if none is provided (not even null)
    // The test currently assumes that the output default is the same as the input default
    private static final Charset ARJ_DEFAULT;
    private static final Charset DUMP_DEFAULT;

    private static final Charset ZIP_DEFAULT = getField(new ZipArchiveInputStream(null),"charset");
    private static final Charset CPIO_DEFAULT = getField(new CpioArchiveInputStream(null),"charset");
    private static final Charset TAR_DEFAULT = getField(new TarArchiveInputStream(null),"charset");
    private static final Charset JAR_DEFAULT = getField(new JarArchiveInputStream(null),"charset");

    static {
        Charset dflt;
        dflt = UNKNOWN;
        try {
            dflt = getField(new ArjArchiveInputStream(Files.newInputStream(getFile("bla.arj").toPath())), "charset");
        } catch (final Exception e) {
            e.printStackTrace();
        }
        ARJ_DEFAULT = dflt;
        dflt = UNKNOWN;
        try {
            dflt = getField(new DumpArchiveInputStream(Files.newInputStream(getFile("bla.dump").toPath())), "charset");
        } catch (final Exception e) {
            e.printStackTrace();
        }
        DUMP_DEFAULT = dflt;
    }

    @Test
    public void testDetect() throws Exception {
        for (final String extension : new String[]{
                ArchiveStreamFactory.AR,
                ArchiveStreamFactory.ARJ,
                ArchiveStreamFactory.CPIO,
                ArchiveStreamFactory.DUMP,
                // Compress doesn't know how to detect JARs, see COMPRESS-91
 //               ArchiveStreamFactory.JAR,
                ArchiveStreamFactory.SEVEN_Z,
                ArchiveStreamFactory.TAR,
                ArchiveStreamFactory.ZIP
        }) {
            assertEquals(extension, detect("bla."+extension));
        }

        try {
            ArchiveStreamFactory.detect(new BufferedInputStream(new ByteArrayInputStream(ByteUtils.EMPTY_BYTE_ARRAY)));
            fail("shouldn't be able to detect empty stream");
        } catch (final ArchiveException e) {
            assertEquals("No Archiver found for the stream signature", e.getMessage());
        }

        try {
            ArchiveStreamFactory.detect(null);
            fail("shouldn't be able to detect null stream");
        } catch (final IllegalArgumentException e) {
            assertEquals("Stream must not be null.", e.getMessage());
        }

        try {
            ArchiveStreamFactory.detect(new BufferedInputStream(new MockEvilInputStream()));
            fail("Expected ArchiveException");
        } catch (final ArchiveException e) {
            assertEquals("IOException while reading signature.", e.getMessage());
        }
    }

    private String detect(final String resource) throws IOException, ArchiveException {
        try(InputStream in = new BufferedInputStream(Files.newInputStream(getFile(resource).toPath()))) {
            return ArchiveStreamFactory.detect(in);
        }
    }

    static final TestData[] TESTS = {
        new TestData("bla.arj", ArchiveStreamFactory.ARJ, false, ARJ_DEFAULT, FACTORY, "charset"),
        new TestData("bla.arj", ArchiveStreamFactory.ARJ, false, StandardCharsets.UTF_8, FACTORY_UTF8, "charset"),
        new TestData("bla.arj", ArchiveStreamFactory.ARJ, false, StandardCharsets.US_ASCII, FACTORY_ASCII, "charset"),

        new TestData("bla.cpio", ArchiveStreamFactory.CPIO, true, CPIO_DEFAULT, FACTORY, "charset"),
        new TestData("bla.cpio", ArchiveStreamFactory.CPIO, true, StandardCharsets.UTF_8, FACTORY_UTF8, "charset"),
        new TestData("bla.cpio", ArchiveStreamFactory.CPIO, true, StandardCharsets.US_ASCII, FACTORY_ASCII, "charset"),

        new TestData("bla.dump", ArchiveStreamFactory.DUMP, false, DUMP_DEFAULT, FACTORY, "charset"),
        new TestData("bla.dump", ArchiveStreamFactory.DUMP, false, StandardCharsets.UTF_8, FACTORY_UTF8, "charset"),
        new TestData("bla.dump", ArchiveStreamFactory.DUMP, false, StandardCharsets.US_ASCII, FACTORY_ASCII, "charset"),

        new TestData("bla.tar", ArchiveStreamFactory.TAR, true, TAR_DEFAULT, FACTORY, "charset"),
        new TestData("bla.tar", ArchiveStreamFactory.TAR, true, StandardCharsets.UTF_8, FACTORY_UTF8, "charset"),
        new TestData("bla.tar", ArchiveStreamFactory.TAR, true, StandardCharsets.US_ASCII, FACTORY_ASCII, "charset"),

        new TestData("bla.jar", ArchiveStreamFactory.JAR, true, JAR_DEFAULT, FACTORY, "charset"),
        new TestData("bla.jar", ArchiveStreamFactory.JAR, true, StandardCharsets.UTF_8, FACTORY_UTF8, "charset"),
        new TestData("bla.jar", ArchiveStreamFactory.JAR, true, StandardCharsets.US_ASCII, FACTORY_ASCII, "charset"),

        new TestData("bla.zip", ArchiveStreamFactory.ZIP, true, ZIP_DEFAULT, FACTORY, "charset"),
        new TestData("bla.zip", ArchiveStreamFactory.ZIP, true, StandardCharsets.UTF_8, FACTORY_UTF8, "charset"),
        new TestData("bla.zip", ArchiveStreamFactory.ZIP, true, StandardCharsets.US_ASCII, FACTORY_ASCII, "charset"),
    };

    @Test
    public void testEncodingInputStreamAutodetect() throws Exception {
        int failed = 0;
        for (int i = 1; i <= TESTS.length; i++) {
            final TestData test = TESTS[i - 1];
            try (final ArchiveInputStream ais = getInputStreamFor(test.testFile, test.fac)) {
                final Charset field = getField(ais, test.fieldName);

                if (!eq(test.expectedCharset, field)) {
                    System.out.println("Failed test " + i + ". expected: " + test.expectedCharset + " actual: " + field
                            + " type: " + test.type);
                    failed++;
                }
            }
        }
        if (failed > 0) {
            fail("Tests failed: " + failed + " out of " + TESTS.length);
        }
    }

    @Test
    public void testEncodingInputStream() throws Exception {
        int failed = 0;
        for (int i = 1; i <= TESTS.length; i++) {
            final TestData test = TESTS[i - 1];
            try (final ArchiveInputStream ais = getInputStreamFor(test.type, test.testFile, test.fac)) {
                final Charset field = getField(ais, test.fieldName);
                if (!eq(test.expectedCharset, field)) {
                    System.out.println("Failed test " + i + ". expected: " + test.expectedCharset + " actual: " + field
                            + " type: " + test.type);
                    failed++;
                }
            }
        }
        if (failed > 0) {
            fail("Tests failed: " + failed + " out of " + TESTS.length);
        }
    }

    @Test
    public void testEncodingOutputStream() throws Exception {
        int failed = 0;
        for(int i = 1; i <= TESTS.length; i++) {
            final TestData test = TESTS[i-1];
            if (test.hasOutputStream) {
                try (final ArchiveOutputStream ais = getOutputStreamFor(test.type, test.fac)) {
                    final Charset field = getField(ais, test.fieldName);
                    if (!eq(test.expectedCharset, field)) {
                        System.out.println("Failed test " + i + ". expected: " + test.expectedCharset + " actual: "
                                + field + " type: " + test.type);
                        failed++;
                    }
                }
            }
        }
        if (failed > 0) {
            fail("Tests failed: " + failed + " out of " + TESTS.length);
        }
    }

    // equals allowing null
    private static boolean eq(final Object exp, final Object act) {
        return Objects.equals(exp, act);
    }

    @SuppressWarnings("deprecation")
    private static Charset getField(final Object instance, final String name) {
        final Class<?> cls = instance.getClass();
        Field fld;
        try {
            fld = cls.getDeclaredField(name);
        } catch (final NoSuchFieldException nsfe) {
                try {
                    fld = cls.getSuperclass().getDeclaredField(name);
                } catch (final NoSuchFieldException e) {
                    System.out.println("Cannot find " + name + " in class " + instance.getClass().getSimpleName());
                    return UNKNOWN;
                }
        }
        final boolean isAccessible = fld.isAccessible();
        try {
            if (!isAccessible) {
                fld.setAccessible(true);
            }
            final Object object = fld.get(instance);

            if (object == null) {
                return StandardCharsets.UTF_8;
            } if (object instanceof Charset) {
                return (Charset) object;
            } else if (object instanceof String) {
                return Charset.forName(((String) object));
            }
            System.out.println("Wrong type: " + object.getClass().getCanonicalName() + " for " + name + " in class " + instance.getClass().getSimpleName());
            return UNKNOWN;
        } catch (final Exception e) {
            e.printStackTrace();
            return UNKNOWN;
        } finally {
            if (!isAccessible) {
                fld.setAccessible(isAccessible);
            }
        }
    }

    private ArchiveInputStream getInputStreamFor(final String resource, final ArchiveStreamFactory factory)
            throws IOException, ArchiveException {
        return factory.createArchiveInputStream(
                   new BufferedInputStream(Files.newInputStream(getFile(resource).toPath())));
    }

    private ArchiveInputStream getInputStreamFor(final String type, final String resource, final ArchiveStreamFactory factory)
            throws IOException, ArchiveException {
        return factory.createArchiveInputStream(
                   type,
                   new BufferedInputStream(Files.newInputStream(getFile(resource).toPath())));
    }

    private ArchiveOutputStream getOutputStreamFor(final String type, final ArchiveStreamFactory factory)
            throws ArchiveException {
        return factory.createArchiveOutputStream(type, new ByteArrayOutputStream());
    }
}
