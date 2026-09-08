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

import static kala.compress.AbstractTest.getFile;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

import kala.compress.utils.ByteUtils;
import kala.compress.utils.TimeUtils;
import org.apache.commons.io.file.attribute.FileTimes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * JUnit tests for org.apache.commons.compress.archivers.zip.ZipEntry.
 */
public class ZipArchiveEntryTest {

    @Test
    public void bestEffortIncludesUnparseableExtraData() throws Exception {
        final ZipExtraField[] extraFields = parsingModeBehaviorTestData();
        final ZipArchiveEntry ze = new ZipArchiveEntry("foo");
        ze.setExtraFields(extraFields);
        final ZipExtraField[] read = ze.getExtraFields(ZipArchiveEntry.ExtraFieldParsingMode.BEST_EFFORT);
        assertEquals(extraFields.length, read.length);
    }

    private ZipExtraField[] parsingModeBehaviorTestData() {
        final AsiExtraField a = new AsiExtraField();
        a.setDirectory(true);
        a.setMode(0755);
        final UnrecognizedExtraField u = new UnrecognizedExtraField();
        u.setHeaderId(ExtraFieldUtilsTest.UNRECOGNIZED_HEADER);
        u.setLocalFileDataData(ByteUtils.EMPTY_BYTE_ARRAY);
        final UnparseableExtraFieldData x = new UnparseableExtraFieldData();
        final byte[] unparseable = { 0, 0, (byte) 0xff, (byte) 0xff, 0, 0, 0 };
        x.parseFromLocalFileData(unparseable, 0, unparseable.length);
        return new ZipExtraField[] { a, u, x };
    }

    /**
     * test handling of extra fields
     */
    @Test
    public void testAddAsFirstExtraField() {
        final AsiExtraField a = new AsiExtraField();
        a.setDirectory(true);
        a.setMode(0755);
        final UnrecognizedExtraField u = new UnrecognizedExtraField();
        u.setHeaderId(ExtraFieldUtilsTest.UNRECOGNIZED_HEADER);
        u.setLocalFileDataData(ByteUtils.EMPTY_BYTE_ARRAY);

        final ZipArchiveEntry ze = new ZipArchiveEntry("test/");
        ze.setExtraFields(new ZipExtraField[] { a, u });
        final byte[] data1 = ze.getExtra();

        final UnrecognizedExtraField u2 = new UnrecognizedExtraField();
        u2.setHeaderId(ExtraFieldUtilsTest.UNRECOGNIZED_HEADER);
        u2.setLocalFileDataData(new byte[] { 1 });

        ze.addAsFirstExtraField(u2);
        final byte[] data2 = ze.getExtra();
        ZipExtraField[] result = ze.getExtraFields();
        assertEquals(2, result.length, "second pass");
        assertSame(u2, result[0]);
        assertSame(a, result[1]);
        assertEquals(data1.length + 1, data2.length, "length second pass");

        final UnrecognizedExtraField u3 = new UnrecognizedExtraField();
        u3.setHeaderId((short) 2);
        u3.setLocalFileDataData(new byte[] { 1 });
        ze.addAsFirstExtraField(u3);
        result = ze.getExtraFields();
        assertEquals(3, result.length, "third pass");
        assertSame(u3, result[0]);
        assertSame(u2, result[1]);
        assertSame(a, result[2]);
    }

    /**
     * Test case for <a href="https://issues.apache.org/jira/browse/COMPRESS-93">COMPRESS-93</a>.
     */
    @Test
    public void testCompressionMethod() throws Exception {
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(new ByteArrayOutputStream())) {
            final ZipArchiveEntry entry = new ZipArchiveEntry("foo");
            assertEquals(-1, entry.getMethod());
            assertFalse(zos.canWriteEntryData(entry));

            entry.setMethod(ZipEntry.STORED);
            assertEquals(ZipEntry.STORED, entry.getMethod());
            assertTrue(zos.canWriteEntryData(entry));

            entry.setMethod(ZipEntry.DEFLATED);
            assertEquals(ZipEntry.DEFLATED, entry.getMethod());
            assertTrue(zos.canWriteEntryData(entry));

            // Test the unsupported "imploded" compression method (6)
            entry.setMethod(6);
            assertEquals(6, entry.getMethod());
            assertFalse(zos.canWriteEntryData(entry));
        }
    }

    @Test
    public void testCopyConstructor() throws Exception {
        final ZipArchiveEntry archiveEntry = new ZipArchiveEntry("fred");
        archiveEntry.setUnixMode(0664);
        archiveEntry.setMethod(ZipEntry.DEFLATED);
        archiveEntry.getGeneralPurposeBit().useStrongEncryption(true);
        final ZipArchiveEntry copy = new ZipArchiveEntry(archiveEntry);
        assertEquals(archiveEntry, copy);
    }

    @Test
    public void testDraconicThrowsOnUnparseableExtraData() throws Exception {
        final ZipExtraField[] extraFields = parsingModeBehaviorTestData();
        final ZipArchiveEntry ze = new ZipArchiveEntry("foo");
        ze.setExtraFields(extraFields);
        assertThrows(ZipException.class, () -> ze.getExtraFields(ZipArchiveEntry.ExtraFieldParsingMode.DRACONIC));
    }

    /**
     * test handling of extra fields via central directory
     */
    @Test
    public void testExtraFieldMerging() {
        final AsiExtraField a = new AsiExtraField();
        a.setDirectory(true);
        a.setMode(0755);
        final UnrecognizedExtraField u = new UnrecognizedExtraField();
        u.setHeaderId(ExtraFieldUtilsTest.UNRECOGNIZED_HEADER);
        u.setLocalFileDataData(ByteUtils.EMPTY_BYTE_ARRAY);

        final ZipArchiveEntry ze = new ZipArchiveEntry("test/");
        ze.setExtraFields(new ZipExtraField[] { a, u });

        // merge
        // Header-ID 1 + length 1 + one byte of data
        final byte[] b = ByteUtils.toLittleEndian(ExtraFieldUtilsTest.UNRECOGNIZED_HEADER, 2);
        ze.setCentralDirectoryExtra(new byte[] { b[0], b[1], 1, 0, 127 });

        ZipExtraField[] result = ze.getExtraFields();
        assertEquals(2, result.length, "first pass");
        assertSame(a, result[0]);
        assertEquals(ExtraFieldUtilsTest.UNRECOGNIZED_HEADER, result[1].getHeaderId());
        assertEquals(0, result[1].getLocalFileDataLength());
        assertEquals(1, result[1].getCentralDirectoryLength());

        // add new
        // Header-ID 2 + length 0
        ze.setCentralDirectoryExtra(new byte[] { 2, 0, 0, 0 });

        result = ze.getExtraFields();
        assertEquals(3, result.length, "second pass");

        // merge
        // Header-ID 2 + length 1 + one byte of data
        ze.setExtra(new byte[] { 2, 0, 1, 0, 127 });

        result = ze.getExtraFields();
        assertEquals(3, result.length, "third pass");
        assertSame(a, result[0]);
        assertEquals(2, result[2].getHeaderId());
        assertEquals(1, result[2].getLocalFileDataLength());
        assertEquals(0, result[2].getCentralDirectoryLength());
    }

    /**
     * test handling of extra fields
     */
    @Test
    public void testExtraFields() {
        final AsiExtraField a = new AsiExtraField();
        a.setDirectory(true);
        a.setMode(0755);
        final UnrecognizedExtraField u = new UnrecognizedExtraField();
        u.setHeaderId(ExtraFieldUtilsTest.UNRECOGNIZED_HEADER);
        u.setLocalFileDataData(ByteUtils.EMPTY_BYTE_ARRAY);

        final ZipArchiveEntry ze = new ZipArchiveEntry("test/");
        ze.setExtraFields(new ZipExtraField[] { a, u });
        final byte[] data1 = ze.getExtra();
        ZipExtraField[] result = ze.getExtraFields();
        assertEquals(2, result.length, "first pass");
        assertSame(a, result[0]);
        assertSame(u, result[1]);

        final UnrecognizedExtraField u2 = new UnrecognizedExtraField();
        u2.setHeaderId(ExtraFieldUtilsTest.UNRECOGNIZED_HEADER);
        u2.setLocalFileDataData(new byte[] { 1 });

        ze.addExtraField(u2);
        final byte[] data2 = ze.getExtra();
        result = ze.getExtraFields();
        assertEquals(2, result.length, "second pass");
        assertSame(a, result[0]);
        assertSame(u2, result[1]);
        assertEquals(data1.length + 1, data2.length, "length second pass");

        final UnrecognizedExtraField u3 = new UnrecognizedExtraField();
        u3.setHeaderId((short) 2);
        u3.setLocalFileDataData(new byte[] { 1 });
        ze.addExtraField(u3);
        result = ze.getExtraFields();
        assertEquals(3, result.length, "third pass");

        ze.removeExtraField(ExtraFieldUtilsTest.UNRECOGNIZED_HEADER);
        final byte[] data3 = ze.getExtra();
        result = ze.getExtraFields();
        assertEquals(2, result.length, "fourth pass");
        assertSame(a, result[0]);
        assertSame(u3, result[1]);
        assertEquals(data2.length, data3.length, "length fourth pass");

        assertThrows(NoSuchElementException.class, () -> ze.removeExtraField(ExtraFieldUtilsTest.UNRECOGNIZED_HEADER), "should be no such element");
    }

    @Test
    public void testIsUnixSymlink() {
        final ZipArchiveEntry ze = new ZipArchiveEntry("foo");
        ze.setUnixMode(UnixStat.LINK_FLAG);
        assertTrue(ze.isUnixSymlink());
        ze.setUnixMode(UnixStat.LINK_FLAG | UnixStat.DIR_FLAG);
        assertFalse(ze.isUnixSymlink());
    }

    /**
     * @see "https://issues.apache.org/jira/browse/COMPRESS-379"
     */
    @Test
    public void testIsUnixSymlinkIsFalseIfMoreThanOneFlagIsSet() throws Exception {
        try (ZipArchiveReader zf = ZipArchiveReader.builder().setFile(getFile("COMPRESS-379.jar")).get()) {
            final ZipArchiveEntry ze = zf.getEntry("META-INF/maven/");
            assertFalse(ze.isUnixSymlink());
        }
    }

    /**
     * Test case for <a href="https://issues.apache.org/jira/browse/COMPRESS-94">COMPRESS-94</a>.
     */
    @Test
    public void testNotEquals() {
        final ZipArchiveEntry entry1 = new ZipArchiveEntry("foo");
        final ZipArchiveEntry entry2 = new ZipArchiveEntry("bar");
        assertNotEquals(entry1, entry2);
    }

    /**
     * Tests comment's influence on equals comparisons.
     *
     * @see "https://issues.apache.org/jira/browse/COMPRESS-187"
     */
    @Test
    public void testNullCommentEqualsEmptyComment() {
        final ZipArchiveEntry entry1 = new ZipArchiveEntry("foo");
        final ZipArchiveEntry entry2 = new ZipArchiveEntry("foo");
        final ZipArchiveEntry entry3 = new ZipArchiveEntry("foo");
        entry1.setComment(null);
        entry2.setComment("");
        entry3.setComment("bar");
        assertEquals(entry1, entry2);
        assertNotEquals(entry1, entry3);
        assertNotEquals(entry2, entry3);
    }

    @Test
    public void testOnlyParseableLenientExcludesUnparseableExtraData() throws Exception {
        final ZipExtraField[] extraFields = parsingModeBehaviorTestData();
        final ZipArchiveEntry ze = new ZipArchiveEntry("foo");
        ze.setExtraFields(extraFields);
        final ZipExtraField[] read = ze.getExtraFields(ZipArchiveEntry.ExtraFieldParsingMode.ONLY_PARSEABLE_LENIENT);
        assertEquals(extraFields.length, read.length + 1);
    }

    @Test
    public void testOnlyParseableStrictExcludesUnparseableExtraData() throws Exception {
        final ZipExtraField[] extraFields = parsingModeBehaviorTestData();
        final ZipArchiveEntry ze = new ZipArchiveEntry("foo");
        ze.setExtraFields(extraFields);
        final ZipExtraField[] read = ze.getExtraFields(ZipArchiveEntry.ExtraFieldParsingMode.ONLY_PARSEABLE_STRICT);
        assertEquals(extraFields.length, read.length + 1);
    }

    @Test
    public void testReparsingUnicodeExtraWithUnsupportedversionThrowsInStrictMode() throws Exception {
        try (ZipArchiveReader zf = ZipArchiveReader.builder().setFile(getFile("COMPRESS-479.zip")).get()) {
            final ZipArchiveEntry ze = zf.getEntry("%U20AC_for_Dollar.txt");
            assertThrows(ZipException.class, () -> ze.getExtraFields(ZipArchiveEntry.ExtraFieldParsingMode.STRICT_FOR_KNOW_EXTRA_FIELDS));
        }
    }

    @Test
    public void testShouldNotSetExtraDateFieldsIfDateFitsInDosDates() {
        final ZipArchiveEntry ze = new ZipArchiveEntry();
        final FileTime time = FileTime.from(ZipUtilTest.toLocalInstant("2022-12-28T20:39:33.1234567"));
        ze.setTime(time);

        assertEquals(time.toMillis(), ze.getTime());
        assertEquals(time.toMillis(), ze.getLastModifiedTime().toMillis());
        assertNull(ze.getExtraField(X5455_ExtendedTimestamp.HEADER_ID));
        assertNull(ze.getExtraField(X000A_NTFS.HEADER_ID));

        final long dosTime = ByteUtils.getUnsignedIntLE(ZipUtil.toDosTime(ze.getTime()), 0);
        ZipUtilTest.assertDosDate(dosTime, 2022, 12, 28, 20, 39, 32); // DOS dates only store even seconds
    }

    /// Replaces a modification time and synchronizes timestamp extra fields across DOS date boundaries.
    @ParameterizedTest
    @CsvSource({
            "1975-11-27T00:00:00, true",
            "1980-01-01T00:00:00, false",
            "1980-01-01T00:00:00.123, false",
            "2022-12-28T20:39:33.123, false",
            "2097-11-27T00:00:00, false",
            "2099-01-01T00:00:00, false",
            "2100-01-01T00:00:00, true",
            "2108-01-01T00:00:00, true"
    })
    public void testReplaceModificationTime(final String date, final boolean requiresExtra) throws Exception {
        final ZipArchiveEntry entry = new ZipArchiveEntry("test");
        entry.setMethod(ZipEntry.STORED);
        entry.setTime(Instant.parse("2020-03-04T12:34:56.123Z").toEpochMilli());
        final long time = ZipUtilTest.toLocalInstant(date).toEpochMilli();
        entry.setTime(time);

        assertEquals(time, entry.getTime());
        assertEquals(time, entry.getLastModifiedTime().toMillis());
        if (requiresExtra) {
            final X000A_NTFS ntfs = (X000A_NTFS) entry.getExtraField(X000A_NTFS.HEADER_ID);
            assertNotNull(ntfs);
            assertEquals(time, ntfs.getModifyFileTime().toMillis());
        } else {
            assertNull(entry.getExtraField(X5455_ExtendedTimestamp.HEADER_ID));
            assertNull(entry.getExtraField(X000A_NTFS.HEADER_ID));
        }

        final FileTime accessTime = FileTime.from(Instant.parse("2021-05-06T01:02:03.1234567Z"));
        entry.setLastAccessTime(accessTime);
        final ZipArchiveEntry copy = new ZipArchiveEntry(entry);
        assertEquals(time, copy.getTime());
        assertEquals(accessTime, copy.getLastAccessTime());
        final X000A_NTFS ntfs = (X000A_NTFS) copy.getExtraField(X000A_NTFS.HEADER_ID);
        if (requiresExtra) {
            assertEquals(time, ntfs.getModifyFileTime().toMillis());
        } else {
            assertNull(ntfs.getModifyFileTime());
        }
    }

    /// Replaces a FileTime without retaining its modification time or changing other timestamps.
    @Test
    public void testSetTimeAfterLastModifiedTime() {
        final ZipArchiveEntry entry = new ZipArchiveEntry("test");
        entry.setLastModifiedTime(FileTime.from(Instant.parse("2020-03-04T12:34:56.1234567Z")));
        final FileTime accessTime = entry.getLastAccessTime();
        final FileTime creationTime = entry.getCreationTime();
        final long time = Instant.parse("2022-12-28T12:39:33.123Z").toEpochMilli();
        entry.setTime(time);

        assertEquals(time, entry.getTime());
        assertEquals(time, entry.getLastModifiedTime().toMillis());
        assertEquals(accessTime, entry.getLastAccessTime());
        assertEquals(creationTime, entry.getCreationTime());
        final X000A_NTFS ntfs = (X000A_NTFS) entry.getExtraField(X000A_NTFS.HEADER_ID);
        if (ntfs != null) {
            assertEquals(time, ntfs.getModifyFileTime().toMillis());
        }
    }

    /// Matches JDK getters after the local-time setter replaces an epoch time.
    @Test
    public void testSetTimeLocalAfterSetTime() {
        final ZipArchiveEntry entry = new ZipArchiveEntry("test");
        entry.setTime(Instant.parse("2020-03-04T12:34:56Z").toEpochMilli());
        final LocalDateTime time = LocalDateTime.parse("2022-12-28T20:39:33.123");
        entry.setTimeLocal(time);

        final ZipEntry expected = new ZipEntry("test");
        expected.setTimeLocal(time);
        assertEquals(expected.getTime(), entry.getTime());
        assertEquals(expected.getLastModifiedTime(), entry.getLastModifiedTime());
    }

    @Test
    public void testShouldNotSetInfoZipFieldIfAnyDatesExceedUnixTime() {
        final ZipArchiveEntry ze = new ZipArchiveEntry();
        final FileTime accessTime = FileTime.from(Instant.parse("2022-12-29T21:40:34.1234567Z"));
        final FileTime creationTime = FileTime.from(Instant.parse("2038-12-28T20:39:33.1234567Z"));
        final long time = Instant.parse("2020-03-04T12:34:56.1234567Z").toEpochMilli();
        ze.setTime(time);
        ze.setLastAccessTime(accessTime);
        ze.setCreationTime(creationTime);

        assertEquals(time, ze.getTime());
        assertEquals(time, ze.getLastModifiedTime().toMillis());
        assertNull(ze.getExtraField(X5455_ExtendedTimestamp.HEADER_ID));
        final X000A_NTFS ntfs = (X000A_NTFS) ze.getExtraField(X000A_NTFS.HEADER_ID);
        assertNotNull(ntfs);
        assertNull(ntfs.getModifyFileTime());
        assertEquals(FileTimes.toNtfsTime(accessTime), ntfs.getAccessTime());
        assertEquals(FileTimes.toNtfsTime(creationTime), ntfs.getCreateTime());
    }

    @Test
    public void testShouldNotSetInfoZipFieldIfDateExceedsUnixTime() {
        final ZipArchiveEntry ze = new ZipArchiveEntry();
        final FileTime time = FileTime.from(ZipUtilTest.toLocalInstant("2138-11-27T00:00:00"));
        ze.setTime(time.toMillis());

        assertEquals(time.toMillis(), ze.getTime());
        assertEquals(time, ze.getLastModifiedTime());
        assertNull(ze.getExtraField(X5455_ExtendedTimestamp.HEADER_ID));
        final X000A_NTFS ntfs = (X000A_NTFS) ze.getExtraField(X000A_NTFS.HEADER_ID);
        assertNotNull(ntfs);
        assertEquals(FileTimes.toNtfsTime(time), ntfs.getModifyTime());
        assertEquals(Long.MIN_VALUE, ntfs.getAccessTime());
        assertEquals(Long.MIN_VALUE, ntfs.getCreateTime());
    }

    @Test
    public void testShouldSetExtraDateFieldsIfAccessDateIsSet() {
        final ZipArchiveEntry ze = new ZipArchiveEntry();
        final FileTime lastAccessTime = FileTime.from(Instant.parse("2022-12-28T20:39:33.1234567Z"));
        final long time = Instant.parse("2020-03-04T12:34:56.1234567Z").toEpochMilli();
        ze.setTime(time);
        ze.setLastAccessTime(lastAccessTime);

        assertEquals(time, ze.getTime());
        assertEquals(time, ze.getLastModifiedTime().toMillis());
        final X5455_ExtendedTimestamp extendedTimestamp = (X5455_ExtendedTimestamp) ze.getExtraField(X5455_ExtendedTimestamp.HEADER_ID);
        assertNotNull(extendedTimestamp);
        assertEquals(TimeUtils.toUnixTime(lastAccessTime), extendedTimestamp.getAccessTime().longValue());
        assertFalse(extendedTimestamp.hasCreateTime());
        final X000A_NTFS ntfs = (X000A_NTFS) ze.getExtraField(X000A_NTFS.HEADER_ID);
        assertNotNull(ntfs);
        assertNull(ntfs.getModifyFileTime());
        assertEquals(FileTimes.toNtfsTime(lastAccessTime), ntfs.getAccessTime());
        assertEquals(Long.MIN_VALUE, ntfs.getCreateTime());
    }

    @Test
    public void testShouldSetExtraDateFieldsIfAllDatesAreSet() {
        final ZipArchiveEntry ze = new ZipArchiveEntry();
        final FileTime accessTime = FileTime.from(Instant.parse("2022-12-29T21:40:34.1234567Z"));
        final FileTime creationTime = FileTime.from(Instant.parse("2022-12-28T20:39:33.1234567Z"));
        final long time = Instant.parse("2020-03-04T12:34:56.1234567Z").toEpochMilli();
        ze.setTime(time);
        ze.setLastAccessTime(accessTime);
        ze.setCreationTime(creationTime);

        assertEquals(time, ze.getTime());
        assertEquals(time, ze.getLastModifiedTime().toMillis());
        final X5455_ExtendedTimestamp extendedTimestamp = (X5455_ExtendedTimestamp) ze.getExtraField(X5455_ExtendedTimestamp.HEADER_ID);
        assertNotNull(extendedTimestamp);
        assertEquals(TimeUtils.toUnixTime(accessTime), extendedTimestamp.getAccessTime().longValue());
        assertEquals(TimeUtils.toUnixTime(creationTime), extendedTimestamp.getCreateTime().longValue());
        final X000A_NTFS ntfs = (X000A_NTFS) ze.getExtraField(X000A_NTFS.HEADER_ID);
        assertNotNull(ntfs);
        assertNull(ntfs.getModifyFileTime());
        assertEquals(FileTimes.toNtfsTime(accessTime), ntfs.getAccessTime());
        assertEquals(FileTimes.toNtfsTime(creationTime), ntfs.getCreateTime());
    }

    @Test
    public void testShouldSetExtraDateFieldsIfCreationDateIsSet() {
        final ZipArchiveEntry ze = new ZipArchiveEntry();
        final FileTime creationTime = FileTime.from(Instant.parse("2022-12-28T20:39:33.1234567Z"));
        final long time = Instant.parse("2020-03-04T12:34:56.1234567Z").toEpochMilli();
        ze.setTime(time);
        ze.setCreationTime(creationTime);

        assertEquals(time, ze.getTime());
        assertEquals(time, ze.getLastModifiedTime().toMillis());
        final X5455_ExtendedTimestamp extendedTimestamp = (X5455_ExtendedTimestamp) ze.getExtraField(X5455_ExtendedTimestamp.HEADER_ID);
        assertNotNull(extendedTimestamp);
        assertFalse(extendedTimestamp.hasAccessTime());
        assertEquals(TimeUtils.toUnixTime(creationTime), extendedTimestamp.getCreateTime().longValue());
        final X000A_NTFS ntfs = (X000A_NTFS) ze.getExtraField(X000A_NTFS.HEADER_ID);
        assertNotNull(ntfs);
        assertNull(ntfs.getModifyFileTime());
        assertEquals(Long.MIN_VALUE, ntfs.getAccessTime());
        assertEquals(FileTimes.toNtfsTime(creationTime), ntfs.getCreateTime());
    }

    @Test
    public void testShouldSetExtraDateFieldsIfDateExceedsDosDate() {
        final ZipArchiveEntry ze = new ZipArchiveEntry();
        final FileTime time = FileTime.from(ZipUtilTest.toLocalInstant("1975-11-27T00:00:00"));
        ze.setTime(time.toMillis());

        assertEquals(time.toMillis(), ze.getTime());
        assertEquals(time, ze.getLastModifiedTime());
        final X5455_ExtendedTimestamp extendedTimestamp = (X5455_ExtendedTimestamp) ze.getExtraField(X5455_ExtendedTimestamp.HEADER_ID);
        assertNotNull(extendedTimestamp);
        assertEquals(TimeUtils.toUnixTime(time), extendedTimestamp.getModifyTime().longValue());
        assertFalse(extendedTimestamp.hasAccessTime());
        assertFalse(extendedTimestamp.hasCreateTime());
        final X000A_NTFS ntfs = (X000A_NTFS) ze.getExtraField(X000A_NTFS.HEADER_ID);
        assertNotNull(ntfs);
        assertEquals(FileTimes.toNtfsTime(time), ntfs.getModifyTime());
        assertEquals(Long.MIN_VALUE, ntfs.getAccessTime());
        assertEquals(Long.MIN_VALUE, ntfs.getCreateTime());
    }

    @Test
    public void testShouldSetExtraDateFieldsIfModifyDateIsExplicitlySet() {
        final ZipArchiveEntry ze = new ZipArchiveEntry();
        final FileTime time = FileTime.from(Instant.parse("2022-12-28T20:39:33.1234567Z"));
        ze.setLastModifiedTime(time);

        assertEquals(time.toMillis(), ze.getTime());
        assertEquals(time, ze.getLastModifiedTime());
        final X5455_ExtendedTimestamp extendedTimestamp = (X5455_ExtendedTimestamp) ze.getExtraField(X5455_ExtendedTimestamp.HEADER_ID);
        assertNotNull(extendedTimestamp);
        assertEquals(TimeUtils.toUnixTime(time), extendedTimestamp.getModifyTime().longValue());
        assertFalse(extendedTimestamp.hasAccessTime());
        assertFalse(extendedTimestamp.hasCreateTime());
        final X000A_NTFS ntfs = (X000A_NTFS) ze.getExtraField(X000A_NTFS.HEADER_ID);
        assertNotNull(ntfs);
        assertEquals(FileTimes.toNtfsTime(time), ntfs.getModifyTime());
        assertEquals(Long.MIN_VALUE, ntfs.getAccessTime());
        assertEquals(Long.MIN_VALUE, ntfs.getCreateTime());
    }

    @Test
    public void testStrictForKnowExtraFieldsIncludesUnparseableExtraData() throws Exception {
        final ZipExtraField[] extraFields = parsingModeBehaviorTestData();
        final ZipArchiveEntry ze = new ZipArchiveEntry("foo");
        ze.setExtraFields(extraFields);
        final ZipExtraField[] read = ze.getExtraFields(ZipArchiveEntry.ExtraFieldParsingMode.STRICT_FOR_KNOW_EXTRA_FIELDS);
        assertEquals(extraFields.length, read.length);
    }

    @Test
    public void testUnixMode() {
        ZipArchiveEntry ze = new ZipArchiveEntry("foo");
        assertEquals(0, ze.getPlatform());
        ze.setUnixMode(0755);
        assertEquals(3, ze.getPlatform());
        assertEquals(0755, ze.getExternalAttributes() >> 16 & 0xFFFF);
        assertEquals(0, ze.getExternalAttributes() & 0xFFFF);

        ze.setUnixMode(0444);
        assertEquals(3, ze.getPlatform());
        assertEquals(0444, ze.getExternalAttributes() >> 16 & 0xFFFF);
        assertEquals(1, ze.getExternalAttributes() & 0xFFFF);

        ze = new ZipArchiveEntry("foo/");
        assertEquals(0, ze.getPlatform());
        ze.setUnixMode(0777);
        assertEquals(3, ze.getPlatform());
        assertEquals(0777, ze.getExternalAttributes() >> 16 & 0xFFFF);
        assertEquals(0x10, ze.getExternalAttributes() & 0xFFFF);

        ze.setUnixMode(0577);
        assertEquals(3, ze.getPlatform());
        assertEquals(0577, ze.getExternalAttributes() >> 16 & 0xFFFF);
        assertEquals(0x11, ze.getExternalAttributes() & 0xFFFF);
    }

    @Test
    public void testZipArchiveClone() throws Exception {
        try (ZipArchiveReader zf = ZipArchiveReader.builder().setFile(getFile("COMPRESS-479.zip")).get()) {
            final ZipArchiveEntry ze = zf.getEntry("%U20AC_for_Dollar.txt");
            final ZipArchiveEntry clonedZe = (ZipArchiveEntry) ze.clone();
            assertEquals(ze, clonedZe);
        }
    }
}
