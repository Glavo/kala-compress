/*
 * Copyright 2026 Glavo
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
package kala.compress.archivers.zip;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;
import java.util.jar.JarEntry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import kala.compress.archivers.jar.JarArchiveEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/// Tests the independently implemented ZIP entry API and its archive representation.
class ZipArchiveEntryApiTest {

    /// Verifies that removing inheritance retains all public ZipEntry methods and constants.
    @Test
    void publicApi() throws Exception {
        assertFalse(ZipEntry.class.isAssignableFrom(ZipArchiveEntry.class));
        for (final Method method : ZipEntry.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic()) {
                continue;
            }
            final Method replacement = ZipArchiveEntry.class.getMethod(method.getName(), method.getParameterTypes());
            assertEquals(method.getReturnType() == ZipEntry.class ? ZipArchiveEntry.class
                    : method.getName().equals("clone") ? ZipArchiveEntry.class : method.getReturnType(), replacement.getReturnType());
        }
        for (final Field field : ZipEntry.class.getFields()) {
            final Field replacement = ZipArchiveEntry.class.getField(field.getName());
            assertEquals(field.getType(), replacement.getType());
            assertTrue(Modifier.isStatic(replacement.getModifiers()));
            assertTrue(Modifier.isFinal(replacement.getModifiers()));
        }
    }

    /// Checks defaults, unsigned CRC boundaries, size handling, and string validation.
    @Test
    void basicMetadata() {
        final ZipArchiveEntry entry = new ZipArchiveEntry("entry");
        assertEquals("entry", entry.toString());
        assertEquals(-1, entry.getMethod());
        assertEquals(-1, entry.getSize());
        assertEquals(-1, entry.getCompressedSize());
        assertEquals(-1, entry.getCrc());
        assertEquals(-1, entry.getTime());
        assertNull(entry.getTimeLocal());
        assertNull(entry.getLastModifiedTime());
        assertNull(entry.getLastAccessTime());
        assertNull(entry.getCreationTime());
        assertNull(entry.getComment());
        assertNull(entry.getExtra());
        assertThrows(NullPointerException.class, () -> new ZipArchiveEntry((String) null));
        assertThrows(IllegalArgumentException.class, () -> new ZipArchiveEntry("x".repeat(65536)));
        entry.setSize(Long.MAX_VALUE);
        entry.setCompressedSize(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, entry.getSize());
        assertEquals(Long.MAX_VALUE, entry.getCompressedSize());
        entry.setCompressedSize(-2);
        assertEquals(-2, entry.getCompressedSize());
        assertThrows(IllegalArgumentException.class, () -> entry.setSize(-1));
        entry.setCrc(0xffffffffL);
        assertEquals(0xffffffffL, entry.getCrc());
        assertThrows(IllegalArgumentException.class, () -> entry.setCrc(-1));
        assertThrows(IllegalArgumentException.class, () -> entry.setCrc(0x100000000L));
        assertEquals(0xffffffffL, entry.getCrc());
        entry.setComment("comment");
        assertEquals("comment", entry.getComment());
        assertThrows(IllegalArgumentException.class, () -> entry.setComment("x".repeat(65536)));
        assertEquals("comment", entry.getComment());
        entry.setComment(null);
        assertNull(entry.getComment());
        assertEquals(65535, new ZipArchiveEntry("x".repeat(65535)).getName().length());
        entry.setComment("x".repeat(65535));
        assertEquals(65535, entry.getComment().length());
    }

    /// Checks fluent time setters and prevents absent NTFS timestamps from becoming 1601 dates.
    @Test
    void independentTimes() {
        final ZipArchiveEntry entry = new ZipArchiveEntry("entry");
        final FileTime time = FileTime.from(Instant.parse("2024-03-04T12:34:56.1234567Z"));
        assertSame(entry, entry.setLastModifiedTime(time));
        assertEquals(time, entry.getLastModifiedTime());
        assertNull(entry.getLastAccessTime());
        assertNull(entry.getCreationTime());
        entry.addExtraField(new JarMarker());
        assertEquals(time, entry.getLastModifiedTime());
        assertNull(entry.getLastAccessTime());
        assertNull(entry.getCreationTime());
        assertSame(entry, entry.setLastAccessTime(time));
        assertSame(entry, entry.setCreationTime(time));
        assertThrows(NullPointerException.class, () -> entry.setLastModifiedTime(null));
        assertThrows(NullPointerException.class, () -> entry.setLastAccessTime(null));
        assertThrows(NullPointerException.class, () -> entry.setCreationTime(null));
        assertThrows(NullPointerException.class, () -> entry.setTimeLocal(null));
        assertEquals(time, entry.getLastModifiedTime());
        assertEquals(time, entry.getLastAccessTime());
        assertEquals(time, entry.getCreationTime());
    }

    /// Replaces stale timestamp extras and preserves dates outside the DOS range on disk.
    @ParameterizedTest
    @CsvSource({
            "1975-01-02T03:04:05.123, true",
            "1980-01-01T00:00:00, true",
            "2024-03-04T12:34:56, false",
            "2107-12-31T23:59:58, false",
            "2108-01-02T03:04:05.123, true"
    })
    void replaceLocalTime(final String text, final boolean extended) throws Exception {
        final ZipArchiveEntry entry = new ZipArchiveEntry("entry");
        entry.setLastModifiedTime(FileTime.from(Instant.parse("2020-01-02T03:04:05Z")));
        final LocalDateTime time = LocalDateTime.parse(text);
        entry.setTimeLocal(time);
        entry.addExtraField(new JarMarker());
        assertEquals(time, entry.getTimeLocal());
        assertEquals(extended, entry.getExtraField(X000A_NTFS.HEADER_ID) != null);
        final byte[] data = write(entry);
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(data))) {
            assertEquals(entry.getTime(), input.getNextEntry().getTime());
        }
        try (ZipArchiveReader reader = ZipArchiveReader.builder().setByteArray(data).get()) {
            assertEquals(time, reader.getEntry("entry").getTimeLocal());
        }
    }

    /// Preserves access and creation times when replacing the local modification time.
    @Test
    void replaceLocalTimeWithOtherTimes() {
        final ZipArchiveEntry entry = new ZipArchiveEntry("entry");
        final FileTime previous = FileTime.from(Instant.parse("2020-01-02T03:04:05.1234567Z"));
        entry.setLastModifiedTime(previous).setLastAccessTime(previous).setCreationTime(previous);
        final LocalDateTime time = LocalDateTime.parse("2024-03-04T12:34:57.1234567");
        entry.setTimeLocal(time);
        entry.addExtraField(new JarMarker());
        assertEquals(time.withNano(123000000), entry.getTimeLocal());
        final X000A_NTFS ntfs = (X000A_NTFS) entry.getExtraField(X000A_NTFS.HEADER_ID);
        assertEquals(entry.getLastModifiedTime(), ntfs.getModifyFileTime());
        assertEquals(previous, entry.getLastAccessTime());
        assertEquals(previous, entry.getCreationTime());
    }

    /// Keeps DOS wall-clock times unchanged across daylight-saving gaps and overlaps.
    @ParameterizedTest
    @ValueSource(strings = {"2024-03-10T02:30:00", "2024-11-03T01:30:00"})
    void daylightSavingRoundTrip(final String text) throws Exception {
        final TimeZone previousZone = TimeZone.getDefault();
        final ZoneId zone = ZoneId.of("America/New_York");
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            final LocalDateTime time = LocalDateTime.parse(text);
            final ZipArchiveEntry entry = new ZipArchiveEntry("entry");
            entry.setTimeLocal(time);
            assertEquals(time, entry.getTimeLocal());
            assertEquals(time.atZone(zone).withLaterOffsetAtOverlap().toInstant().toEpochMilli(), entry.getTime());
            final ZipEntry jdkEntry = new ZipEntry("entry");
            jdkEntry.setTimeLocal(time);
            assertEquals(time, new ZipArchiveEntry(jdkEntry).getTimeLocal());
            final byte[] data = write(entry);
            for (final boolean ignoreLocal : new boolean[]{false, true}) {
                try (ZipArchiveReader reader = ZipArchiveReader.builder().setByteArray(data).setIgnoreLocalFileHeader(ignoreLocal).get()) {
                    final ZipArchiveEntry read = reader.getEntry("entry");
                    assertEquals(time, read.getTimeLocal());
                    assertEquals(entry.getTime(), read.getTime());
                    assertEquals(entry.getDosTime(), read.getDosTime());
                }
            }
            try (ZipArchiveInputStream input = new ZipArchiveInputStream(new ByteArrayInputStream(data))) {
                assertEquals(time, input.getNextEntry().getTimeLocal());
            }
        } finally {
            TimeZone.setDefault(previousZone);
        }
    }

    /// Retains DOS local times across time-zone changes, as JDK ZipEntry does.
    @Test
    void changedDefaultTimeZone() throws Exception {
        final TimeZone previousZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            final ZipArchiveEntry epochEntry = new ZipArchiveEntry("entry");
            final ZipEntry jdkEntry = new ZipEntry("entry");
            final long epoch = Instant.parse("2024-03-04T12:34:56Z").toEpochMilli();
            epochEntry.setTime(epoch);
            jdkEntry.setTime(epoch);
            final ZipArchiveEntry local = new ZipArchiveEntry("entry");
            final LocalDateTime localTime = LocalDateTime.of(2024, 3, 4, 12, 34, 56);
            local.setTimeLocal(localTime);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            assertEquals(localTime, local.getTimeLocal());
            assertEquals(jdkEntry.getTimeLocal(), epochEntry.getTimeLocal());
            assertEquals(jdkEntry.getTime(), epochEntry.getTime());
            for (final ZipArchiveEntry entry : new ZipArchiveEntry[]{epochEntry, local}) {
                try (ZipArchiveReader reader = ZipArchiveReader.builder().setByteArray(write(entry)).get()) {
                    assertEquals(entry.getTimeLocal(), reader.getEntry("entry").getTimeLocal());
                    assertEquals(entry.getTime(), reader.getEntry("entry").getTime());
                }
            }
        } finally {
            TimeZone.setDefault(previousZone);
        }
    }

    /// Compares modification-time setters and serialized DOS timestamps with the JDK implementation.
    @ParameterizedTest
    @ValueSource(strings = {
            "1975-01-02T03:04:05.123", "1980-01-01T00:00:00", "1980-01-01T00:00:00.123",
            "1980-01-01T00:00:01.999", "2024-03-04T12:34:57.123", "2099-12-31T23:59:59.999",
            "2100-01-01T00:00:00", "2107-12-31T23:59:59.999", "2108-01-01T00:00:00",
            "2138-01-02T03:04:05.123"
    })
    void jdkTimeCompatibility(final String text) throws Exception {
        final LocalDateTime local = LocalDateTime.parse(text);
        final FileTime time = FileTime.from(local.atZone(ZoneId.systemDefault()).toInstant());
        for (int setter = 0; setter < 3; setter++) {
            final ZipArchiveEntry entry = new ZipArchiveEntry("entry");
            final ZipEntry expected = new ZipEntry("entry");
            switch (setter) {
                case 0:
                    entry.setTime(time.toMillis());
                    expected.setTime(time.toMillis());
                    break;
                case 1:
                    entry.setTimeLocal(local);
                    expected.setTimeLocal(local);
                    break;
                default:
                    entry.setLastModifiedTime(time);
                    expected.setLastModifiedTime(time);
                    break;
            }
            assertEquals(expected.getTime(), entry.getTime());
            assertEquals(expected.getTimeLocal(), entry.getTimeLocal());
            assertEquals(expected.getLastModifiedTime(), entry.getLastModifiedTime());
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            expected.setMethod(ZipEntry.STORED);
            expected.setSize(0);
            expected.setCrc(0);
            try (ZipOutputStream output = new ZipOutputStream(bytes)) {
                output.putNextEntry(expected);
                output.closeEntry();
            }
            assertEquals(ZipLong.getValue(bytes.toByteArray(), ZipEntry.LOCTIM),
                    ZipLong.getValue(write(entry), ZipEntry.LOCTIM));
        }
    }

    /// Keeps a DOS modification time local when access-time extras are generated.
    @Test
    void accessTimeDoesNotPromoteDosTime() throws Exception {
        final TimeZone previousZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            final ZipArchiveEntry entry = new ZipArchiveEntry("entry");
            final ZipEntry expected = new ZipEntry("entry");
            final long time = Instant.parse("2024-03-04T12:34:56Z").toEpochMilli();
            entry.setTime(time);
            expected.setTime(time);
            entry.setLastAccessTime(FileTime.fromMillis(time));
            entry.addExtraField(new JarMarker());
            final ZipArchiveEntry copy = new ZipArchiveEntry(entry);
            final ZipArchiveEntry clone = entry.clone();
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            for (final ZipArchiveEntry other : new ZipArchiveEntry[]{entry, copy, clone}) {
                assertEquals(expected.getTime(), other.getTime());
                assertEquals(expected.getTimeLocal(), other.getTimeLocal());
            }
            entry.setLastAccessTime(FileTime.fromMillis(time));
            try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(write(entry)))) {
                assertEquals(expected.getTime(), input.getNextEntry().getTime());
            }
        } finally {
            TimeZone.setDefault(previousZone);
        }
    }

    /// Retains normalization of malformed DOS fields without eagerly converting them on read.
    @Test
    void malformedDosTime() throws Exception {
        final ZipArchiveEntry entry = new ZipArchiveEntry("entry");
        entry.setDosTime(0);
        final byte[] data = write(entry);
        try (ZipArchiveReader reader = ZipArchiveReader.builder().setByteArray(data).get()) {
            final ZipArchiveEntry read = reader.getEntry("entry");
            assertEquals(0, read.getDosTime());
            assertEquals(LocalDateTime.of(1979, 11, 30, 0, 0), read.getTimeLocal());
            assertEquals(ZipUtil.dosToJavaTime(0), read.getTime());
        }
    }

    /// Keeps an explicitly specified epoch millisecond of -1 when writing an entry.
    @Test
    void negativeEpochTime() throws Exception {
        final ZipArchiveEntry entry = new ZipArchiveEntry("entry");
        entry.setTime(-1);
        try (ZipArchiveReader reader = ZipArchiveReader.builder().setByteArray(write(entry)).get()) {
            assertEquals(-1, reader.getEntry("entry").getTime());
            assertEquals(FileTime.fromMillis(-1), reader.getEntry("entry").getLastModifiedTime());
        }
    }

    /// Copies entry state and preserves the runtime type of cloned JAR entries.
    @Test
    void copyAndClone() throws Exception {
        final JarArchiveEntry entry = new JarArchiveEntry("entry");
        entry.setMethod(ZipMethod.BZIP2.getCode());
        entry.setSize(123);
        entry.setCompressedSize(45);
        entry.setCrc(0xabcdef01L);
        entry.setComment("comment");
        entry.setTimeLocal(LocalDateTime.of(2024, 3, 4, 12, 34, 57, 123000000));
        entry.setUnixMode(0755);
        entry.setVersionRequired(46);
        entry.setVersionMadeBy(63);
        entry.setRawFlag(0x800);
        entry.setDiskNumberStart(2);
        entry.setNameSource(ZipArchiveEntry.NameSource.UNICODE_EXTRA_FIELD);
        entry.setCommentSource(ZipArchiveEntry.CommentSource.UNICODE_EXTRA_FIELD);
        entry.addExtraField(new JarMarker());
        final ZipArchiveEntry copy = new ZipArchiveEntry(entry);
        final ZipArchiveEntry clone = entry.clone();
        assertEquals(JarArchiveEntry.class, clone.getClass());
        assertEquals(entry, clone);
        for (final ZipArchiveEntry other : new ZipArchiveEntry[]{copy, clone}) {
            assertEquals(entry.getMethod(), other.getMethod());
            assertEquals(entry.getTimeLocal(), other.getTimeLocal());
            assertEquals(entry.getVersionRequired(), other.getVersionRequired());
            assertEquals(entry.getVersionMadeBy(), other.getVersionMadeBy());
            assertEquals(entry.getRawFlag(), other.getRawFlag());
            assertEquals(entry.getDiskNumberStart(), other.getDiskNumberStart());
            assertEquals(entry.getNameSource(), other.getNameSource());
            assertEquals(entry.getCommentSource(), other.getCommentSource());
            assertNotSame(entry.getExtra(), other.getExtra());
            other.getGeneralPurposeBit().useUTF8ForNames(true);
            assertFalse(entry.getGeneralPurposeBit().usesUTF8ForNames());
        }
    }

    /// Imports JDK entries with unknown metadata and with explicitly supplied timestamps.
    @Test
    void importJdkEntry() throws Exception {
        final ZipEntry original = new ZipEntry("entry");
        final ZipArchiveEntry empty = new ZipArchiveEntry(original);
        assertEquals(-1, empty.getMethod());
        assertEquals(-1, empty.getSize());
        assertEquals(-1, empty.getCrc());
        assertNull(empty.getLastModifiedTime());
        final FileTime time = FileTime.from(Instant.parse("2024-03-04T12:34:56.1234567Z"));
        original.setMethod(ZipEntry.STORED);
        original.setSize(123);
        original.setCompressedSize(123);
        original.setCrc(0xffffffffL);
        original.setComment("comment");
        original.setLastModifiedTime(time).setLastAccessTime(time).setCreationTime(time);
        final ZipArchiveEntry entry = new ZipArchiveEntry(original);
        assertEquals(original.getName(), entry.getName());
        assertEquals(original.getMethod(), entry.getMethod());
        assertEquals(original.getSize(), entry.getSize());
        assertEquals(original.getCompressedSize(), entry.getCompressedSize());
        assertEquals(original.getCrc(), entry.getCrc());
        assertEquals(original.getComment(), entry.getComment());
        assertEquals(time, entry.getLastModifiedTime());
        assertEquals(time, entry.getLastAccessTime());
        assertEquals(time, entry.getCreationTime());
        final JarArchiveEntry jar = new JarArchiveEntry(new JarEntry(original));
        assertEquals(entry.getLastModifiedTime(), jar.getLastModifiedTime());
        final ZipArchiveEntry reparsed = new ZipArchiveEntry("entry");
        reparsed.setExtra(entry.getExtra());
        assertEquals(time, reparsed.getLastModifiedTime());
        assertEquals(time, reparsed.getLastAccessTime());
        assertEquals(time, reparsed.getCreationTime());
    }

    /// Writes an empty STORED entry to an in-memory ZIP archive.
    private static byte[] write(final ZipArchiveEntry entry) throws Exception {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        entry.setMethod(ZipArchiveEntry.STORED);
        entry.setSize(0);
        entry.setCrc(0);
        try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(bytes)) {
            output.putArchiveEntry(entry);
            output.closeArchiveEntry();
        }
        return bytes.toByteArray();
    }
}
