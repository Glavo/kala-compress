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

    /// Preserves incomplete extra headers and rejects oversized input before parsing or merging.
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void incompleteExtraHeader(final int length) {
        final byte[] tail = new byte[length];
        final ZipArchiveEntry entry = new ZipArchiveEntry("entry");
        entry.setExtra(tail);
        assertArrayEquals(tail, entry.getExtra());
        final ZipArchiveEntry central = new ZipArchiveEntry("entry");
        central.setCentralDirectoryExtra(tail);
        assertArrayEquals(tail, central.getCentralDirectoryExtra());

        final byte[] oversized = new byte[65535 + length];
        ZipShort.putShort(0x5555, oversized, 0);
        ZipShort.putShort(65531, oversized, 2);
        assertThrows(IllegalArgumentException.class, () -> entry.setExtra(oversized));
        assertArrayEquals(tail, entry.getExtra());
        assertArrayEquals(tail, entry.getUnparseableExtraFieldData().getLocalFileDataData());
    }

    /// Leaves timestamps, serialized data, and existing extra field objects unchanged when a time setter fails.
    @ParameterizedTest
    @ValueSource(strings = {"modified", "accessed", "created", "millis", "fileTime", "local"})
    void timeSetterFailureIsAtomic(final String setter) {
        final FileTime replacement = FileTime.from(Instant.parse("1960-03-04T12:34:57Z"));
        for (final boolean existingTime : new boolean[]{false, true}) {
            final ZipArchiveEntry entry = new ZipArchiveEntry("entry");
            entry.setTimeLocal(LocalDateTime.of(2024, 3, 4, 12, 34, 57, 123000000));
            if (existingTime) {
                if (setter.equals("created")) {
                    entry.setLastAccessTime(replacement);
                } else {
                    entry.setCreationTime(replacement);
                }
            }
            final UnrecognizedExtraField padding = new UnrecognizedExtraField();
            padding.setHeaderId(new ZipShort(0x5555));
            padding.setLocalFileDataData(new byte[(existingTime ? 65535 : 65500) - entry.getExtra().length - 4]);
            entry.addExtraField(padding);
            final byte[] extra = entry.getExtra().clone();
            final byte[] central = entry.getCentralDirectoryExtra();
            final ZipExtraField[] fields = entry.getExtraFields(true);
            final long millis = entry.getTime();
            final LocalDateTime local = entry.getTimeLocal();
            final FileTime modified = entry.getLastModifiedTime();
            final FileTime accessed = entry.getLastAccessTime();
            final FileTime created = entry.getCreationTime();
            assertThrows(IllegalArgumentException.class, () -> {
                switch (setter) {
                    case "modified" -> entry.setLastModifiedTime(replacement);
                    case "accessed" -> entry.setLastAccessTime(replacement);
                    case "created" -> entry.setCreationTime(replacement);
                    case "millis" -> entry.setTime(replacement.toMillis());
                    case "fileTime" -> entry.setTime(replacement);
                    case "local" -> entry.setTimeLocal(LocalDateTime.of(1960, 3, 4, 12, 34, 57));
                    default -> fail("Unknown setter: " + setter);
                }
            });
            assertEquals(millis, entry.getTime());
            assertEquals(local, entry.getTimeLocal());
            assertEquals(modified, entry.getLastModifiedTime());
            assertEquals(accessed, entry.getLastAccessTime());
            assertEquals(created, entry.getCreationTime());
            assertArrayEquals(extra, entry.getExtra());
            assertArrayEquals(central, entry.getCentralDirectoryExtra());
            final ZipExtraField[] remaining = entry.getExtraFields(true);
            assertEquals(fields.length, remaining.length);
            for (int i = 0; i < fields.length; i++) {
                assertSame(fields[i], remaining[i]);
            }
        }
    }

    /// Reads NTFS values whose Unix epoch adjustment cannot fit in a signed count of 100-nanosecond units.
    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE + 1, Long.MIN_VALUE + 116444736000000000L - 1})
    void extremeNtfsTimestamp(final long value) {
        final X000A_NTFS ntfs = new X000A_NTFS();
        ntfs.setModifyTime(new ZipEightByteInteger(value));
        final byte[] extra = ExtraFieldUtils.mergeLocalFileDataData(new ZipExtraField[]{ntfs});
        final ZipArchiveEntry entry = new ZipArchiveEntry("entry");
        entry.setExtra(extra);
        final ZipEntry jdk = new ZipEntry("entry");
        jdk.setExtra(extra);
        // JDK truncates the NTFS count to microseconds toward zero.
        assertEquals(jdk.getLastModifiedTime().toInstant(), entry.getLastModifiedTime().toInstant().plusNanos(-(value % 10) * 100));
        assertEquals(ntfs.getModifyFileTime(), entry.getLastModifiedTime());
        assertArrayEquals(extra, entry.getExtra());
    }

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

    /// Reapplies remaining timestamp fields after removing either of two conflicting timestamp fields.
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void removeTimestampExtraField(final boolean removeNtfs) throws Exception {
        final FileTime unixTime = FileTime.from(Instant.parse("2020-03-04T12:34:56Z"));
        final FileTime ntfsTime = FileTime.from(Instant.parse("2024-03-04T12:34:56.1234567Z"));
        final X5455_ExtendedTimestamp unix = new X5455_ExtendedTimestamp();
        unix.setModifyFileTime(unixTime);
        unix.setAccessFileTime(unixTime);
        unix.setCreateFileTime(unixTime);
        final X000A_NTFS ntfs = new X000A_NTFS();
        ntfs.setModifyFileTime(ntfsTime);
        ntfs.setAccessFileTime(ntfsTime);
        ntfs.setCreateFileTime(ntfsTime);
        final ZipArchiveEntry entry = new ZipArchiveEntry("entry");
        entry.setExtraFields(new ZipExtraField[]{unix, ntfs});
        assertEquals(ntfsTime, entry.getLastModifiedTime());

        final ZipShort removed = removeNtfs ? X000A_NTFS.HEADER_ID : X5455_ExtendedTimestamp.HEADER_ID;
        entry.removeExtraField(removed);
        assertNull(entry.getExtraField(removed));
        final FileTime expected = removeNtfs ? unixTime : ntfsTime;
        assertEquals(expected.toMillis(), entry.getTime());
        assertEquals(expected, entry.getLastModifiedTime());
        assertEquals(expected, entry.getLastAccessTime());
        assertEquals(expected, entry.getCreationTime());
        final ZipArchiveEntry reparsed = new ZipArchiveEntry("entry");
        reparsed.setExtra(entry.getExtra());
        assertEquals(reparsed.getLastModifiedTime(), entry.getLastModifiedTime());
        assertEquals(reparsed.getLastAccessTime(), entry.getLastAccessTime());
        assertEquals(reparsed.getCreationTime(), entry.getCreationTime());
        try (ZipArchiveReader reader = ZipArchiveReader.builder().setByteArray(write(entry)).get()) {
            assertEquals(expected, reader.getEntry("entry").getLastModifiedTime());
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
        assertNull(ntfs.getModifyFileTime());
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
            final ZipEntry jdkEntry = new ZipEntry("entry");
            jdkEntry.setTimeLocal(time);
            assertEquals(jdkEntry.getTime(), entry.getTime());
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

    /// Matches JDK epoch-time conversion on both sides of a daylight-saving overlap.
    @ParameterizedTest
    @ValueSource(strings = {"2024-11-03T05:30:01.123Z", "2024-11-03T06:30:01.123Z"})
    void epochTimeInOverlap(final String text) {
        final TimeZone previousZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            final long time = Instant.parse(text).toEpochMilli();
            final ZipArchiveEntry entry = new ZipArchiveEntry("entry");
            final ZipEntry expected = new ZipEntry("entry");
            entry.setTime(time);
            expected.setTime(time);
            assertEquals(expected.getTime(), entry.getTime());
            assertEquals(expected.getTimeLocal(), entry.getTimeLocal());
            assertEquals(expected.getLastModifiedTime(), entry.getLastModifiedTime());
        } finally {
            TimeZone.setDefault(previousZone);
        }
    }

    /// Matches JDK overlap resolution when a local time requires an extended timestamp.
    @Test
    void extendedLocalTimeInOverlap() {
        final TimeZone previousZone = TimeZone.getDefault();
        final ZoneId zone = ZoneId.of("America/New_York");
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            final LocalDateTime time = zone.getRules().nextTransition(Instant.parse("2108-10-01T00:00:00Z"))
                    .getDateTimeAfter().plusMinutes(30);
            final ZipArchiveEntry entry = new ZipArchiveEntry("entry");
            final ZipEntry expected = new ZipEntry("entry");
            entry.setTimeLocal(time);
            expected.setTimeLocal(time);
            assertEquals(expected.getTime(), entry.getTime());
            assertEquals(expected.getLastModifiedTime(), entry.getLastModifiedTime());
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
            try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(write(entry)))) {
                assertEquals(expected.getTime(), input.getNextEntry().getTime());
            }
            try (ZipArchiveReader reader = ZipArchiveReader.builder().setByteArray(write(copy)).get()) {
                assertEquals(expected.getTime(), reader.getEntry("entry").getTime());
            }
        } finally {
            TimeZone.setDefault(previousZone);
        }
    }

    /// Preserves timestamp presence and values when Kala archives are read by the JDK.
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    void timestampPresenceRoundTrip(final int mask) throws Exception {
        final ZipArchiveEntry entry = new ZipArchiveEntry("entry");
        entry.setTime(Instant.parse("2024-03-04T12:34:56Z").toEpochMilli());
        final FileTime precise = FileTime.from(Instant.parse("2024-03-04T12:34:57.1234567Z"));
        if ((mask & 1) != 0) {
            entry.setLastModifiedTime(precise);
        }
        if ((mask & 2) != 0) {
            entry.setLastAccessTime(precise);
        }
        if ((mask & 4) != 0) {
            entry.setCreationTime(precise);
        }
        if (mask != 0) {
            final X5455_ExtendedTimestamp unix = (X5455_ExtendedTimestamp) entry.getExtraField(X5455_ExtendedTimestamp.HEADER_ID);
            final X000A_NTFS ntfs = (X000A_NTFS) entry.getExtraField(X000A_NTFS.HEADER_ID);
            assertEquals((mask & 1) != 0, unix.isBit0_modifyTimePresent());
            assertEquals((mask & 1) != 0, ntfs.getModifyFileTime() != null);
        }
        final byte[] bytes = write(entry);
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            final ZipEntry read = input.getNextEntry();
            assertEquals(entry.getTime(), read.getTime());
            if ((mask & 2) != 0) {
                assertEquals(precise.toMillis(), read.getLastAccessTime().toMillis());
            } else {
                assertNull(read.getLastAccessTime());
            }
            if ((mask & 4) != 0) {
                assertEquals(precise.toMillis(), read.getCreationTime().toMillis());
            } else {
                assertNull(read.getCreationTime());
            }
        }
        for (final boolean ignoreLocal : new boolean[]{false, true}) {
            try (ZipArchiveReader reader = ZipArchiveReader.builder().setByteArray(bytes).setIgnoreLocalFileHeader(ignoreLocal).get()) {
                final ZipArchiveEntry read = reader.getEntry("entry");
                assertEquals(entry.getLastModifiedTime(), read.getLastModifiedTime());
                assertEquals(entry.getLastAccessTime(), read.getLastAccessTime());
                assertEquals(entry.getCreationTime(), read.getCreationTime());
            }
        }
    }

    /// Reads NTFS missing-time markers generated by JDK ZipOutputStream.
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 7})
    void readJdkNtfsTimestamps(final int mask) throws Exception {
        final ZipEntry source = new ZipEntry("entry");
        source.setTime(Instant.parse("2024-03-04T12:34:56Z").toEpochMilli());
        final FileTime future = FileTime.from(Instant.parse("2100-03-04T12:34:57.123456Z"));
        if ((mask & 1) != 0) {
            source.setLastModifiedTime(future);
        }
        if ((mask & 2) != 0) {
            source.setLastAccessTime(future);
        }
        if ((mask & 4) != 0) {
            source.setCreationTime(future);
        }
        source.setMethod(ZipEntry.STORED);
        source.setSize(0);
        source.setCrc(0);
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes)) {
            output.putNextEntry(source);
            output.closeEntry();
        }
        try (ZipArchiveReader reader = ZipArchiveReader.builder().setByteArray(bytes.toByteArray()).get()) {
            final ZipArchiveEntry read = reader.getEntry("entry");
            assertEquals(source.getLastModifiedTime(), read.getLastModifiedTime());
            assertEquals(source.getLastAccessTime(), read.getLastAccessTime());
            assertEquals(source.getCreationTime(), read.getCreationTime());
        }
        try (ZipArchiveInputStream input = new ZipArchiveInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            final ZipArchiveEntry read = input.getNextEntry();
            assertEquals(source.getLastModifiedTime(), read.getLastModifiedTime());
            assertEquals(source.getLastAccessTime(), read.getLastAccessTime());
            assertEquals(source.getCreationTime(), read.getCreationTime());
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

    /// Recovers NTFS precision from JDK entries and preserves later changes made through JDK setters.
    @ParameterizedTest
    @ValueSource(strings = {"1960-03-04T12:34:56.1234567Z", "2024-03-04T12:34:56.1234567Z"})
    void importNtfsPrecision(final String text) throws Exception {
        final FileTime modified = FileTime.from(Instant.parse(text));
        final FileTime accessed = FileTime.from(modified.toInstant().plusSeconds(1));
        final FileTime created = FileTime.from(modified.toInstant().minusSeconds(1));
        final X000A_NTFS ntfs = new X000A_NTFS();
        ntfs.setModifyFileTime(modified);
        ntfs.setAccessFileTime(accessed);
        ntfs.setCreateFileTime(created);
        final ZipArchiveEntry holder = new ZipArchiveEntry("entry");
        holder.addExtraField(ntfs);
        final ZipEntry source = new ZipEntry("entry");
        source.setExtra(holder.getExtra());

        for (final ZipArchiveEntry copy : new ZipArchiveEntry[]{new ZipArchiveEntry(source), new JarArchiveEntry(source)}) {
            assertEquals(modified, copy.getLastModifiedTime());
            assertEquals(accessed, copy.getLastAccessTime());
            assertEquals(created, copy.getCreationTime());
            final ZipArchiveEntry reparsed = new ZipArchiveEntry("entry");
            reparsed.setExtra(copy.getExtra());
            assertEquals(modified, reparsed.getLastModifiedTime());
            assertEquals(accessed, reparsed.getLastAccessTime());
            assertEquals(created, reparsed.getCreationTime());
        }

        final FileTime replacement = FileTime.from(modified.toInstant().plusSeconds(100));
        source.setLastModifiedTime(replacement).setLastAccessTime(replacement).setCreationTime(replacement);
        final ZipArchiveEntry changed = new ZipArchiveEntry(source);
        assertEquals(replacement, changed.getLastModifiedTime());
        assertEquals(replacement, changed.getLastAccessTime());
        assertEquals(replacement, changed.getCreationTime());
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
