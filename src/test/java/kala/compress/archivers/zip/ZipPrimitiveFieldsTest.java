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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/// Tests raw unsigned values and presence states after removing numeric wrapper classes.
class ZipPrimitiveFieldsTest {

    /// Preserves every ZIP64 field combination, including zero and values with the sign bit set.
    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE, Long.MAX_VALUE})
    void zip64PresenceAndBits(final long value) throws Exception {
        for (int mask = 0; mask < 16; mask++) {
            final Zip64ExtendedInformationExtraField field = new Zip64ExtendedInformationExtraField();
            final ByteBuffer expected = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN);
            if ((mask & 1) != 0) {
                field.setSize(value);
                expected.putLong(value);
            }
            if ((mask & 2) != 0) {
                field.setCompressedSize(value);
                expected.putLong(value);
            }
            if ((mask & 4) != 0) {
                field.setRelativeHeaderOffset(value);
                expected.putLong(value);
            }
            if ((mask & 8) != 0) {
                field.setDiskStartNumber((int) value);
                expected.putInt((int) value);
            }
            expected.flip();
            final byte[] bytes = new byte[expected.remaining()];
            expected.get(bytes);
            assertArrayEquals(bytes, field.getCentralDirectoryData());
            assertEquals(bytes.length, field.getCentralDirectoryLength());
            final Zip64ExtendedInformationExtraField parsed = new Zip64ExtendedInformationExtraField();
            parsed.parseFromCentralDirectoryData(bytes, 0, bytes.length);
            parsed.reparseCentralDirectoryData((mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0, (mask & 8) != 0);
            assertPresence(parsed, mask, value);
            assertArrayEquals(bytes, parsed.getCentralDirectoryData());
            parsed.clearSize();
            parsed.clearCompressedSize();
            parsed.clearRelativeHeaderOffset();
            parsed.clearDiskStartNumber();
            assertPresence(parsed, 0, value);
            assertEquals(0, parsed.getLocalFileDataLength());
            assertEquals(0, parsed.getCentralDirectoryLength());
        }
    }

    /// Checks that absence is distinct from every raw value and absent getters fail explicitly.
    private static void assertPresence(final Zip64ExtendedInformationExtraField field, final int mask, final long value) {
        assertEquals((mask & 1) != 0, field.hasSize());
        assertEquals((mask & 2) != 0, field.hasCompressedSize());
        assertEquals((mask & 4) != 0, field.hasRelativeHeaderOffset());
        assertEquals((mask & 8) != 0, field.hasDiskStartNumber());
        if (field.hasSize()) {
            assertEquals(value, field.getSize());
        } else {
            assertThrows(IllegalStateException.class, field::getSize);
        }
        if (field.hasCompressedSize()) {
            assertEquals(value, field.getCompressedSize());
        } else {
            assertThrows(IllegalStateException.class, field::getCompressedSize);
        }
        if (field.hasRelativeHeaderOffset()) {
            assertEquals(value, field.getRelativeHeaderOffset());
        } else {
            assertThrows(IllegalStateException.class, field::getRelativeHeaderOffset);
        }
        if (field.hasDiskStartNumber()) {
            assertEquals((int) value, field.getDiskStartNumber());
        } else {
            assertThrows(IllegalStateException.class, field::getDiskStartNumber);
        }
    }

    /// Keeps stored signed Unix seconds independent of serialization flags and missing timestamps.
    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void extendedTimestampPresence(final int seconds) throws Exception {
        final X5455_ExtendedTimestamp field = new X5455_ExtendedTimestamp();
        assertThrows(IllegalStateException.class, field::getModifyTime);
        field.setModifyTime(seconds);
        field.setAccessTime(seconds);
        field.setCreateTime(seconds);
        final byte[] bytes = ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN)
                .put((byte) 7).putInt(seconds).putInt(seconds).putInt(seconds).array();
        assertArrayEquals(bytes, field.getLocalFileDataData());
        field.setFlags((byte) 0);
        assertArrayEquals(new byte[]{0}, field.getLocalFileDataData());
        assertTrue(field.hasModifyTime());
        assertTrue(field.hasAccessTime());
        assertTrue(field.hasCreateTime());
        assertEquals(seconds, field.getModifyTime());
        assertEquals(FileTime.from(seconds, TimeUnit.SECONDS), field.getModifyFileTime());
        field.setFlags((byte) 7);
        assertArrayEquals(bytes, field.getLocalFileDataData());
        final X5455_ExtendedTimestamp parsed = new X5455_ExtendedTimestamp();
        parsed.parseFromCentralDirectoryData(field.getCentralDirectoryData(), 0, 5);
        assertTrue(parsed.hasModifyTime());
        assertEquals(seconds, parsed.getModifyTime());
        assertFalse(parsed.hasAccessTime());
        assertFalse(parsed.hasCreateTime());
        assertThrows(IllegalStateException.class, parsed::getAccessTime);
        assertThrows(IllegalStateException.class, parsed::getCreateTime);
        field.clearModifyTime();
        field.clearAccessTime();
        field.clearCreateTime();
        assertFalse(field.hasModifyTime());
        assertFalse(field.hasAccessTime());
        assertFalse(field.hasCreateTime());
        assertNull(field.getModifyFileTime());
        assertArrayEquals(new byte[]{0}, field.getLocalFileDataData());
    }

    /// Preserves high-bit field identifiers and unsigned 16-bit lengths in lookup and serialization.
    @ParameterizedTest
    @ValueSource(ints = {0, 0x8000, 0xffff})
    void unsignedHeaderId(final int id) throws Exception {
        final UnrecognizedExtraField field = new UnrecognizedExtraField();
        assertFalse(field.hasHeaderId());
        assertThrows(IllegalStateException.class, field::getHeaderId);
        field.setHeaderId((short) id);
        field.setLocalFileDataData(new byte[0x8000]);
        final ZipArchiveEntry entry = new ZipArchiveEntry("entry");
        entry.addExtraField(field);
        assertSame(field, entry.getExtraField((short) id));
        final byte[] bytes = entry.getExtra();
        assertEquals((byte) id, bytes[0]);
        assertEquals((byte) (id >>> 8), bytes[1]);
        assertEquals((byte) 0x80, bytes[3]);
        final ZipExtraField parsed = ExtraFieldUtils.parse(bytes)[0];
        assertEquals((short) id, parsed.getHeaderId());
        assertEquals(0x8000, parsed.getLocalFileDataLength());
        entry.removeExtraField((short) id);
        assertNull(entry.getExtraField((short) id));
        field.clearHeaderId();
        assertFalse(field.hasHeaderId());
        assertThrows(IllegalStateException.class, field::getHeaderId);
    }

    /// Keeps zero and the NTFS missing marker distinct as raw bytes while both decode as absent.
    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE, Long.MAX_VALUE})
    void ntfsRawValues(final long value) throws Exception {
        final X000A_NTFS field = new X000A_NTFS();
        field.setModifyTime(value);
        field.setAccessTime(value);
        field.setCreateTime(value);
        final byte[] bytes = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0).putShort((short) 1).putShort((short) 24)
                .putLong(value).putLong(value).putLong(value).array();
        assertArrayEquals(bytes, field.getLocalFileDataData());
        final X000A_NTFS parsed = new X000A_NTFS();
        parsed.parseFromLocalFileData(bytes, 0, bytes.length);
        assertEquals(value, parsed.getModifyTime());
        assertEquals(field, parsed);
        assertEquals(field.hashCode(), parsed.hashCode());
        parsed.setModifyFileTime(null);
        assertEquals(Long.MIN_VALUE, parsed.getModifyTime());
        assertNull(parsed.getModifyFileTime());
        assertEquals(value, parsed.getAccessTime());
    }
}
