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
package kala.compress.archivers.zip;

import static kala.compress.AbstractTest.getFile;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.zip.ZipException;
import java.util.Arrays;
import java.math.BigInteger;

import kala.compress.utils.ByteUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class X7875_NewUnixTest {

    /// Checks minimal encoding at every byte-length boundary against an independent BigInteger representation.
    @Test
    void testEncodingBoundaries() throws ZipException {
        assertEncoding(0);
        assertEncoding(1);
        for (int bits = 8; bits < Long.SIZE; bits += 8) {
            assertEncoding((1L << bits) - 1);
            assertEncoding(1L << bits);
        }
        assertEncoding(Long.MAX_VALUE);
    }

    /// Checks the bytes, length, and parsed values for an identifier and its complementary GID.
    private void assertEncoding(final long uid) throws ZipException {
        final long gid = Long.MAX_VALUE - uid;
        xf.setUID(uid);
        xf.setGID(gid);
        final byte[] uidBytes = BigInteger.valueOf(uid).toByteArray();
        final byte[] gidBytes = BigInteger.valueOf(gid).toByteArray();
        final int uidLength = uidBytes.length > 1 && uidBytes[0] == 0 ? uidBytes.length - 1 : uidBytes.length;
        final int gidLength = gidBytes.length > 1 && gidBytes[0] == 0 ? gidBytes.length - 1 : gidBytes.length;
        final byte[] expected = new byte[3 + uidLength + gidLength];
        expected[0] = 1;
        expected[1] = (byte) uidLength;
        expected[2 + uidLength] = (byte) gidLength;
        for (int i = 0; i < uidLength; i++) {
            expected[2 + i] = uidBytes[uidBytes.length - 1 - i];
        }
        for (int i = 0; i < gidLength; i++) {
            expected[3 + uidLength + i] = gidBytes[gidBytes.length - 1 - i];
        }
        assertArrayEquals(expected, xf.getLocalFileDataData());
        assertEquals(expected.length, xf.getLocalFileDataLength());
        final X7875_NewUnix parsed = new X7875_NewUnix();
        parsed.parseFromLocalFileData(expected, 0, expected.length);
        assertEquals(uid, parsed.getUID());
        assertEquals(gid, parsed.getGID());
        assertEquals(xf, parsed);
        assertEquals(xf.hashCode(), parsed.hashCode());
    }

    /// Preserves the unsigned interpretation of negative int inputs and rejects smaller values without changing state.
    @ParameterizedTest
    @ValueSource(longs = {-1, -2, Integer.MIN_VALUE})
    void testNegativeInputs(final long value) {
        xf.setUID(value);
        xf.setGID(value);
        final long expected = Integer.toUnsignedLong((int) value);
        assertEquals(expected, xf.getUID());
        assertEquals(expected, xf.getGID());
        for (final long invalid : new long[]{Integer.MIN_VALUE - 1L, Long.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> xf.setUID(invalid));
            assertThrows(IllegalArgumentException.class, () -> xf.setGID(invalid));
            assertEquals(expected, xf.getUID());
            assertEquals(expected, xf.getGID());
        }
    }

    /// Accepts the maximum encoded length when excess high-order bytes are zero, including nonzero array offsets.
    @ParameterizedTest
    @ValueSource(ints = {8, 9, 255})
    void testZeroPaddedValues(final int valueLength) throws ZipException {
        final int offset = 5;
        final int length = 3 + 2 * valueLength;
        final byte[] data = new byte[offset + length + 2];
        Arrays.fill(data, 0, offset, (byte) 0xff);
        data[offset] = 1;
        data[offset + 1] = (byte) valueLength;
        data[offset + 2 + valueLength] = (byte) valueLength;
        data[offset + 2] = (byte) 0xff;
        ByteUtils.setLongLE(data, offset + 3 + valueLength, Long.MAX_VALUE);
        xf.parseFromLocalFileData(data, offset, length);
        assertEquals(255, xf.getUID());
        assertEquals(Long.MAX_VALUE, xf.getGID());
        assertEquals(12, xf.getLocalFileDataLength());
    }

    /// Rejects oversized identifiers and preserves their bytes through the default entry parsing policy.
    @ParameterizedTest
    @ValueSource(ints = {8, 9, 255})
    void testOversizedValues(final int valueLength) {
        for (final boolean oversizedUid : new boolean[]{false, true}) {
            final byte[] data = new byte[valueLength + 4];
            data[0] = 1;
            if (oversizedUid) {
                data[1] = (byte) valueLength;
                data[1 + valueLength] = (byte) 0x80;
                data[2 + valueLength] = 1;
            } else {
                data[1] = 1;
                data[3] = (byte) valueLength;
                data[data.length - 1] = (byte) 0x80;
            }
            assertThrows(ZipException.class, () -> xf.parseFromLocalFileData(data, 0, data.length));
            final byte[] extra = new byte[4 + data.length];
            ByteUtils.setUnsignedShortLE(extra, 0, 0x7875);
            ByteUtils.setUnsignedShortLE(extra, 2, data.length);
            System.arraycopy(data, 0, extra, 4, data.length);
            final ZipArchiveEntry entry = new ZipArchiveEntry("entry");
            entry.setExtra(extra);
            assertInstanceOf(UnrecognizedExtraField.class, entry.getExtraField(X7875));
            assertArrayEquals(extra, entry.getExtra());
        }
    }

    /// Rejects missing size bytes and identifier data at the declared field boundary.
    @Test
    void testTruncatedValues() {
        for (final byte[] data : new byte[][]{{1, 0}, {1, 2, 0, 1}, {1, 1, 0, 2, 0}}) {
            assertThrows(ZipException.class, () -> xf.parseFromLocalFileData(data, 0, data.length));
        }
    }

    private static final short X7875 = (short) 0x7875;

    private X7875_NewUnix xf;

    @BeforeEach
    public void before() {
        xf = new X7875_NewUnix();
    }

    private void parseReparse(final long uid, final long gid, final byte[] expected, final long expectedUID, final long expectedGID) throws ZipException {

        // Initial local parse (init with garbage to avoid defaults causing test to pass).
        xf.setUID(54321);
        xf.setGID(12345);
        xf.parseFromLocalFileData(expected, 0, expected.length);
        assertEquals(expectedUID, xf.getUID());
        assertEquals(expectedGID, xf.getGID());

        xf.setUID(uid);
        xf.setGID(gid);
        if (expected.length < 5) {
            // We never emit zero-length entries.
            assertEquals(5, xf.getLocalFileDataLength());
        } else {
            assertEquals(expected.length, xf.getLocalFileDataLength());
        }
        byte[] result = xf.getLocalFileDataData();
        if (expected.length < 5) {
            // We never emit zero-length entries.
            assertArrayEquals(new byte[] { 1, 1, 0, 1, 0 }, result);
        } else {
            assertArrayEquals(expected, result);
        }

        // And now we re-parse:
        xf.parseFromLocalFileData(result, 0, result.length);

        // Did uid/gid change from re-parse? They shouldn't!
        assertEquals(expectedUID, xf.getUID());
        assertEquals(expectedGID, xf.getGID());

        assertEquals(0, xf.getCentralDirectoryLength());
        result = xf.getCentralDirectoryData();
        assertArrayEquals(ByteUtils.EMPTY_BYTE_ARRAY, result);

        // And now we re-parse:
        xf.parseFromCentralDirectoryData(result, 0, result.length);

        // Did uid/gid change from 2nd re-parse? They shouldn't!
        assertEquals(expectedUID, xf.getUID());
        assertEquals(expectedGID, xf.getGID());
    }

    @Test
    public void testGetHeaderId() {
        assertEquals(X7875, xf.getHeaderId());
    }

    @Test
    public void testMisc() throws Exception {
        assertNotEquals(xf, new Object());
        assertTrue(xf.toString().startsWith("0x7875 Zip Extra Field"));
        final Object o = xf.clone();
        assertEquals(o.hashCode(), xf.hashCode());
        assertEquals(xf, o);
        xf.setUID(12345);
        assertNotEquals(xf, o);
    }

    @Test
    public void testParseReparse() throws ZipException {

        // Version=1, Len=0, Len=0.
        final byte[] ZERO_LEN = { 1, 0, 0 };

        // Version=1, Len=1, zero, Len=1, zero.
        final byte[] ZERO_UID_GID = { 1, 1, 0, 1, 0 };

        // Version=1, Len=1, one, Len=1, one
        final byte[] ONE_UID_GID = { 1, 1, 1, 1, 1 };

        // Version=1, Len=2, one thousand, Len=2, one thousand
        final byte[] ONE_THOUSAND_UID_GID = { 1, 2, -24, 3, 2, -24, 3 };

        // (2^32 - 2). I guess they avoid (2^32 - 1) since it's identical to -1 in
        // two's complement, and -1 often has a special meaning.
        final byte[] UNIX_MAX_UID_GID = { 1, 4, -2, -1, -1, -1, 4, -2, -1, -1, -1 };

        // Version=1, Len=5, 2^32, Len=5, 2^32 + 1
        // Esoteric test: can we handle 40 bit numbers?
        final byte[] LENGTH_5 = { 1, 5, 0, 0, 0, 0, 1, 5, 1, 0, 0, 0, 1 };

        // Version=1, Len=8, 2^63 - 2, Len=8, 2^63 - 1
        // Esoteric test: can we handle 64-bit numbers?
        final byte[] LENGTH_8 = { 1, 8, -2, -1, -1, -1, -1, -1, -1, 127, 8, -1, -1, -1, -1, -1, -1, -1, 127 };

        final long TWO_TO_32 = 0x100000000L;
        final long MAX = TWO_TO_32 - 2;

        parseReparse(0, 0, ZERO_LEN, 0, 0);
        parseReparse(0, 0, ZERO_UID_GID, 0, 0);
        parseReparse(1, 1, ONE_UID_GID, 1, 1);
        parseReparse(1000, 1000, ONE_THOUSAND_UID_GID, 1000, 1000);
        parseReparse(MAX, MAX, UNIX_MAX_UID_GID, MAX, MAX);
        parseReparse(-2, -2, UNIX_MAX_UID_GID, MAX, MAX);
        parseReparse(TWO_TO_32, TWO_TO_32 + 1, LENGTH_5, TWO_TO_32, TWO_TO_32 + 1);
        parseReparse(Long.MAX_VALUE - 1, Long.MAX_VALUE, LENGTH_8, Long.MAX_VALUE - 1, Long.MAX_VALUE);

        // We never emit this, but we should be able to parse it:
        final byte[] SPURIOUS_ZEROES_1 = { 1, 4, -1, 0, 0, 0, 4, -128, 0, 0, 0 };
        final byte[] EXPECTED_1 = { 1, 1, -1, 1, -128 };
        xf.parseFromLocalFileData(SPURIOUS_ZEROES_1, 0, SPURIOUS_ZEROES_1.length);

        assertEquals(255, xf.getUID());
        assertEquals(128, xf.getGID());
        assertArrayEquals(EXPECTED_1, xf.getLocalFileDataData());

        final byte[] SPURIOUS_ZEROES_2 = { 1, 4, -1, -1, 0, 0, 4, 1, 2, 0, 0 };
        final byte[] EXPECTED_2 = { 1, 2, -1, -1, 2, 1, 2 };
        xf.parseFromLocalFileData(SPURIOUS_ZEROES_2, 0, SPURIOUS_ZEROES_2.length);

        assertEquals(65535, xf.getUID());
        assertEquals(513, xf.getGID());
        assertArrayEquals(EXPECTED_2, xf.getLocalFileDataData());
    }

    @Test
    public void testSampleFile() throws Exception {
        try (ZipArchiveReader zf = ZipArchiveReader.builder().setFile(getFile("COMPRESS-211_uid_gid_zip_test.zip")).get()) {
            // We expect EVERY entry of this ZIP file (dir & file) to
            // contain extra field 0x7875.
            for (ZipArchiveEntry zae : zf.getEntries()) {
                final String name = zae.getName();
                final X7875_NewUnix xf = (X7875_NewUnix) zae.getExtraField(X7875);

                // The directory entry in the test ZIP file is uid/gid 1000.
                long expected = 1000;
                if (name.contains("uid555_gid555")) {
                    expected = 555;
                } else if (name.contains("uid5555_gid5555")) {
                    expected = 5555;
                } else if (name.contains("uid55555_gid55555")) {
                    expected = 55555;
                } else if (name.contains("uid555555_gid555555")) {
                    expected = 555555;
                } else if (name.contains("min_unix")) {
                    expected = 0;
                } else if (name.contains("max_unix")) {
                    // 2^32-2 was the biggest UID/GID I could create on my Linux!
                    // (December 2012, Linux kernel 3.4)
                    expected = 0x100000000L - 2;
                }
                assertEquals(expected, xf.getUID());
                assertEquals(expected, xf.getGID());
            }
        }
    }

}
