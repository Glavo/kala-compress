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

import kala.compress.utils.ByteUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.zip.ZipException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit tests for org.apache.commons.compress.archivers.zip.ExtraFieldUtils.
 */
@SuppressWarnings("OctalInteger")
public class ExtraFieldUtilsTest implements UnixStat {

    /// Applies the selected parsing policy to incomplete headers in either extra data location.
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void testIncompleteHeader(final int length) throws Exception {
        for (final boolean local : new boolean[]{false, true}) {
            for (final int prefixLength : new int[]{0, data.length}) {
                final byte[] bytes = Arrays.copyOf(data, prefixLength + length);
                Arrays.fill(bytes, prefixLength, bytes.length, (byte) 1);
                final ZipExtraField[] fields = ExtraFieldUtils.parse(bytes, local, ExtraFieldUtils.UnparseableExtraField.READ);
                assertInstanceOf(UnparseableExtraFieldData.class, fields[fields.length - 1]);
                assertArrayEquals(bytes, local ? ExtraFieldUtils.mergeLocalFileDataData(fields)
                        : ExtraFieldUtils.mergeCentralDirectoryData(fields));
                final ZipExtraField[] skipped = ExtraFieldUtils.parse(bytes, local, ExtraFieldUtils.UnparseableExtraField.SKIP);
                assertEquals(prefixLength == 0 ? 0 : 2, skipped.length);
                assertThrows(ZipException.class, () -> ExtraFieldUtils.parse(bytes, local, ExtraFieldUtils.UnparseableExtraField.THROW));
            }
        }
    }

    /**
     * Header-ID of a ZipExtraField not supported by Commons Compress.
     *
     * <p>
     * Used to be short(1) but this is the ID of the Zip64 extra field.
     * </p>
     */
    static final short UNRECOGNIZED_HEADER = (short) 0x5555;

    /**
     * Header-ID of a ZipExtraField not supported by Commons Compress used for the ArrayIndexOutOfBoundsTest.
     */
    static final short AIOB_HEADER = (short) 0x1000;
    private AsiExtraField a;
    private UnrecognizedExtraField dummy;
    private byte[] data;

    private byte[] aLocal;

    @BeforeEach
    public void setUp() {
        a = new AsiExtraField();
        a.setMode(0755);
        a.setDirectory(true);
        dummy = new UnrecognizedExtraField();
        dummy.setHeaderId(UNRECOGNIZED_HEADER);
        dummy.setLocalFileDataData(new byte[] { 0 });
        dummy.setCentralDirectoryData(new byte[] { 0 });

        aLocal = a.getLocalFileDataData();
        final byte[] dummyLocal = dummy.getLocalFileDataData();
        data = new byte[4 + aLocal.length + 4 + dummyLocal.length];
        System.arraycopy(ByteUtils.toLittleEndian(a.getHeaderId(), 2), 0, data, 0, 2);
        System.arraycopy(ByteUtils.toLittleEndian(a.getLocalFileDataLength(), 2), 0, data, 2, 2);
        System.arraycopy(aLocal, 0, data, 4, aLocal.length);
        System.arraycopy(ByteUtils.toLittleEndian(dummy.getHeaderId(), 2), 0, data, 4 + aLocal.length, 2);
        System.arraycopy(ByteUtils.toLittleEndian(dummy.getLocalFileDataLength(), 2), 0, data, 4 + aLocal.length + 2, 2);
        System.arraycopy(dummyLocal, 0, data, 4 + aLocal.length + 4, dummyLocal.length);

    }

    /**
     * Test merge methods
     */
    @Test
    public void testMerge() {
        final byte[] local = ExtraFieldUtils.mergeLocalFileDataData(new ZipExtraField[] { a, dummy });
        assertEquals(data.length, local.length, "local length");
        for (int i = 0; i < local.length; i++) {
            assertEquals(data[i], local[i], "local byte " + i);
        }

        final byte[] dummyCentral = dummy.getCentralDirectoryData();
        final byte[] data2 = new byte[4 + aLocal.length + 4 + dummyCentral.length];
        System.arraycopy(data, 0, data2, 0, 4 + aLocal.length + 2);
        System.arraycopy(ByteUtils.toLittleEndian(dummy.getCentralDirectoryLength(), 2), 0, data2, 4 + aLocal.length + 2, 2);
        System.arraycopy(dummyCentral, 0, data2, 4 + aLocal.length + 4, dummyCentral.length);

        final byte[] central = ExtraFieldUtils.mergeCentralDirectoryData(new ZipExtraField[] { a, dummy });
        assertEquals(data2.length, central.length, "central length");
        for (int i = 0; i < central.length; i++) {
            assertEquals(data2[i], central[i], "central byte " + i);
        }

    }

    @Test
    public void testMergeWithUnparseableData() throws Exception {
        final ZipExtraField d = new UnparseableExtraFieldData();
        final byte[] b = ByteUtils.toLittleEndian(UNRECOGNIZED_HEADER, 2);
        d.parseFromLocalFileData(new byte[] { b[0], b[1], 1, 0 }, 0, 4);
        final byte[] local = ExtraFieldUtils.mergeLocalFileDataData(new ZipExtraField[] { a, d });
        assertEquals(data.length - 1, local.length, "local length");
        for (int i = 0; i < local.length; i++) {
            assertEquals(data[i], local[i], "local byte " + i);
        }

        final byte[] dCentral = d.getCentralDirectoryData();
        final byte[] data2 = new byte[4 + aLocal.length + dCentral.length];
        System.arraycopy(data, 0, data2, 0, 4 + aLocal.length + 2);
        System.arraycopy(dCentral, 0, data2, 4 + aLocal.length, dCentral.length);

        final byte[] central = ExtraFieldUtils.mergeCentralDirectoryData(new ZipExtraField[] { a, d });
        assertEquals(data2.length, central.length, "central length");
        for (int i = 0; i < central.length; i++) {
            assertEquals(data2[i], central[i], "central byte " + i);
        }

    }

    /**
     * test parser.
     */
    @Test
    public void testParse() throws Exception {
        final ZipExtraField[] ze = ExtraFieldUtils.parse(data);
        assertEquals(2, ze.length, "number of fields");
        assertInstanceOf(AsiExtraField.class, ze[0], "type field 1");
        assertEquals(040755, ((AsiExtraField) ze[0]).getMode(), "mode field 1");
        assertInstanceOf(UnrecognizedExtraField.class, ze[1], "type field 2");
        assertEquals(1, ze[1].getLocalFileDataLength(), "data length field 2");

        final byte[] data2 = new byte[data.length - 1];
        System.arraycopy(data, 0, data2, 0, data2.length);
        final Exception e = assertThrows(Exception.class, () -> ExtraFieldUtils.parse(data2), "data should be invalid");
        assertEquals("Bad extra field starting at " + (4 + aLocal.length) + ".  Block length of 1 bytes exceeds remaining data of 0 bytes.", e.getMessage(),
                "message");
    }

    @Test
    public void testParseCentral() throws Exception {
        final ZipExtraField[] ze = ExtraFieldUtils.parse(data, false);
        assertEquals(2, ze.length, "number of fields");
        assertInstanceOf(AsiExtraField.class, ze[0], "type field 1");
        assertEquals(040755, ((AsiExtraField) ze[0]).getMode(), "mode field 1");
        assertInstanceOf(UnrecognizedExtraField.class, ze[1], "type field 2");
        assertEquals(1, ze[1].getCentralDirectoryLength(), "data length field 2");

    }

    @Test
    public void testParseWithRead() throws Exception {
        ZipExtraField[] ze = ExtraFieldUtils.parse(data, true, ExtraFieldUtils.UnparseableExtraField.READ);
        assertEquals(2, ze.length, "number of fields");
        assertInstanceOf(AsiExtraField.class, ze[0], "type field 1");
        assertEquals(040755, ((AsiExtraField) ze[0]).getMode(), "mode field 1");
        assertInstanceOf(UnrecognizedExtraField.class, ze[1], "type field 2");
        assertEquals(1, ze[1].getLocalFileDataLength(), "data length field 2");

        final byte[] data2 = new byte[data.length - 1];
        System.arraycopy(data, 0, data2, 0, data2.length);
        ze = ExtraFieldUtils.parse(data2, true, ExtraFieldUtils.UnparseableExtraField.READ);
        assertEquals(2, ze.length, "number of fields");
        assertInstanceOf(AsiExtraField.class, ze[0], "type field 1");
        assertEquals(040755, ((AsiExtraField) ze[0]).getMode(), "mode field 1");
        assertInstanceOf(UnparseableExtraFieldData.class, ze[1], "type field 2");
        assertEquals(4, ze[1].getLocalFileDataLength(), "data length field 2");
        for (int i = 0; i < 4; i++) {
            assertEquals(data2[data.length - 5 + i], ze[1].getLocalFileDataData()[i], "byte number " + i);
        }
    }

    @Test
    public void testParseWithSkip() throws Exception {
        ZipExtraField[] ze = ExtraFieldUtils.parse(data, true, ExtraFieldUtils.UnparseableExtraField.SKIP);
        assertEquals(2, ze.length, "number of fields");
        assertInstanceOf(AsiExtraField.class, ze[0], "type field 1");
        assertEquals(040755, ((AsiExtraField) ze[0]).getMode(), "mode field 1");
        assertInstanceOf(UnrecognizedExtraField.class, ze[1], "type field 2");
        assertEquals(1, ze[1].getLocalFileDataLength(), "data length field 2");

        final byte[] data2 = new byte[data.length - 1];
        System.arraycopy(data, 0, data2, 0, data2.length);
        ze = ExtraFieldUtils.parse(data2, true, ExtraFieldUtils.UnparseableExtraField.SKIP);
        assertEquals(1, ze.length, "number of fields");
        assertInstanceOf(AsiExtraField.class, ze[0], "type field 1");
        assertEquals(040755, ((AsiExtraField) ze[0]).getMode(), "mode field 1");
    }
}
