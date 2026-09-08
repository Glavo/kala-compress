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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.zip.ZipException;

import org.junit.jupiter.api.Test;

public class Zip64ExtendedInformationExtraFieldTest {

    private static final long SIZE = 0x12345678;
    private static final long CSIZE = 0x9ABCDEF;
    private static final long OFF = 0xABCDEF0912345678L;
    private static final int DISK = 0x12;

    private static void checkDisk(final byte[] b, final int off) {
        assertEquals(0x12, b[0 + off]);
        assertEquals(0x00, b[1 + off]);
        assertEquals(0x00, b[2 + off]);
        assertEquals(0x00, b[3 + off]);
    }

    private static void checkOffset(final byte[] b, final int off) {
        assertEquals(0x78, b[0 + off]);
        assertEquals(0x56, b[1 + off]);
        assertEquals(0x34, b[2 + off]);
        assertEquals(0x12, b[3 + off]);
        assertEquals((byte) 0x09, b[4 + off]);
        assertEquals((byte) 0xEF, b[5 + off]);
        assertEquals((byte) 0xCD, b[6 + off]);
        assertEquals((byte) 0xAB, b[7 + off]);
    }

    private static void checkSizes(final byte[] b) {
        assertEquals(0x78, b[0]);
        assertEquals(0x56, b[1]);
        assertEquals(0x34, b[2]);
        assertEquals(0x12, b[3]);
        assertEquals(0x00, b[4]);
        assertEquals(0x00, b[5]);
        assertEquals(0x00, b[6]);
        assertEquals(0x00, b[7]);
        assertEquals((byte) 0xEF, b[8]);
        assertEquals((byte) 0xCD, b[9]);
        assertEquals((byte) 0xAB, b[10]);
        assertEquals(0x09, b[11]);
        assertEquals(0x00, b[12]);
        assertEquals(0x00, b[13]);
        assertEquals(0x00, b[14]);
        assertEquals(0x00, b[15]);
    }

    @Test
    public void testReadCDSizesAndOffset() throws ZipException {
        final Zip64ExtendedInformationExtraField f = new Zip64ExtendedInformationExtraField();
        final byte[] b = new byte[24];
        System.arraycopy(ByteUtils.toLittleEndian(SIZE, 8), 0, b, 0, 8);
        System.arraycopy(ByteUtils.toLittleEndian(CSIZE, 8), 0, b, 8, 8);
        System.arraycopy(ByteUtils.toLittleEndian(OFF, 8), 0, b, 16, 8);
        f.parseFromCentralDirectoryData(b, 0, b.length);
        assertEquals(SIZE, f.getSize());
        assertEquals(CSIZE, f.getCompressedSize());
        assertEquals(OFF, f.getRelativeHeaderOffset());
        assertFalse(f.hasDiskStartNumber());
    }

    @Test
    public void testReadCDSizesOffsetAndDisk() throws ZipException {
        final Zip64ExtendedInformationExtraField f = new Zip64ExtendedInformationExtraField();
        final byte[] b = new byte[28];
        System.arraycopy(ByteUtils.toLittleEndian(SIZE, 8), 0, b, 0, 8);
        System.arraycopy(ByteUtils.toLittleEndian(CSIZE, 8), 0, b, 8, 8);
        System.arraycopy(ByteUtils.toLittleEndian(OFF, 8), 0, b, 16, 8);
        System.arraycopy(ByteUtils.toLittleEndian(DISK, 4), 0, b, 24, 4);
        f.parseFromCentralDirectoryData(b, 0, b.length);
        assertEquals(SIZE, f.getSize());
        assertEquals(CSIZE, f.getCompressedSize());
        assertEquals(OFF, f.getRelativeHeaderOffset());
        assertEquals(DISK, f.getDiskStartNumber());
    }

    @Test
    public void testReadCDSomethingAndDisk() throws ZipException {
        final Zip64ExtendedInformationExtraField f = new Zip64ExtendedInformationExtraField();
        final byte[] b = new byte[12];
        System.arraycopy(ByteUtils.toLittleEndian(SIZE, 8), 0, b, 0, 8);
        System.arraycopy(ByteUtils.toLittleEndian(DISK, 4), 0, b, 8, 4);
        f.parseFromCentralDirectoryData(b, 0, b.length);
        assertFalse(f.hasSize());
        assertFalse(f.hasCompressedSize());
        assertFalse(f.hasRelativeHeaderOffset());
        assertEquals(DISK, f.getDiskStartNumber());
    }

    @Test
    public void testReadLFHSizesAndDisk() throws ZipException {
        final Zip64ExtendedInformationExtraField f = new Zip64ExtendedInformationExtraField();
        final byte[] b = new byte[20];
        System.arraycopy(ByteUtils.toLittleEndian(SIZE, 8), 0, b, 0, 8);
        System.arraycopy(ByteUtils.toLittleEndian(CSIZE, 8), 0, b, 8, 8);
        System.arraycopy(ByteUtils.toLittleEndian(DISK, 4), 0, b, 16, 4);
        f.parseFromLocalFileData(b, 0, b.length);
        assertEquals(SIZE, f.getSize());
        assertEquals(CSIZE, f.getCompressedSize());
        assertFalse(f.hasRelativeHeaderOffset());
        assertEquals(DISK, f.getDiskStartNumber());
    }

    @Test
    public void testReadLFHSizesAndOffset() throws ZipException {
        final Zip64ExtendedInformationExtraField f = new Zip64ExtendedInformationExtraField();
        final byte[] b = new byte[24];
        System.arraycopy(ByteUtils.toLittleEndian(SIZE, 8), 0, b, 0, 8);
        System.arraycopy(ByteUtils.toLittleEndian(CSIZE, 8), 0, b, 8, 8);
        System.arraycopy(ByteUtils.toLittleEndian(OFF, 8), 0, b, 16, 8);
        f.parseFromLocalFileData(b, 0, b.length);
        assertEquals(SIZE, f.getSize());
        assertEquals(CSIZE, f.getCompressedSize());
        assertEquals(OFF, f.getRelativeHeaderOffset());
        assertFalse(f.hasDiskStartNumber());
    }

    @Test
    public void testReadLFHSizesOffsetAndDisk() throws ZipException {
        final Zip64ExtendedInformationExtraField f = new Zip64ExtendedInformationExtraField();
        final byte[] b = new byte[28];
        System.arraycopy(ByteUtils.toLittleEndian(SIZE, 8), 0, b, 0, 8);
        System.arraycopy(ByteUtils.toLittleEndian(CSIZE, 8), 0, b, 8, 8);
        System.arraycopy(ByteUtils.toLittleEndian(OFF, 8), 0, b, 16, 8);
        System.arraycopy(ByteUtils.toLittleEndian(DISK, 4), 0, b, 24, 4);
        f.parseFromLocalFileData(b, 0, b.length);
        assertEquals(SIZE, f.getSize());
        assertEquals(CSIZE, f.getCompressedSize());
        assertEquals(OFF, f.getRelativeHeaderOffset());
        assertEquals(DISK, f.getDiskStartNumber());
    }

    @Test
    public void testReadLFHSizesOnly() throws ZipException {
        final Zip64ExtendedInformationExtraField f = new Zip64ExtendedInformationExtraField();
        final byte[] b = new byte[16];
        System.arraycopy(ByteUtils.toLittleEndian(SIZE, 8), 0, b, 0, 8);
        System.arraycopy(ByteUtils.toLittleEndian(CSIZE, 8), 0, b, 8, 8);
        f.parseFromLocalFileData(b, 0, b.length);
        assertEquals(SIZE, f.getSize());
        assertEquals(CSIZE, f.getCompressedSize());
        assertFalse(f.hasRelativeHeaderOffset());
        assertFalse(f.hasDiskStartNumber());
    }

    @Test
    public void testReparseCDSingleEightByteData() throws ZipException {
        final Zip64ExtendedInformationExtraField f = new Zip64ExtendedInformationExtraField();
        final byte[] b = new byte[8];
        System.arraycopy(ByteUtils.toLittleEndian(SIZE, 8), 0, b, 0, 8);
        f.parseFromCentralDirectoryData(b, 0, b.length);
        f.reparseCentralDirectoryData(true, false, false, false);
        assertEquals(SIZE, f.getSize());
        assertFalse(f.hasCompressedSize());
        assertFalse(f.hasRelativeHeaderOffset());
        assertFalse(f.hasDiskStartNumber());
        f.clearSize();
        f.reparseCentralDirectoryData(false, true, false, false);
        assertFalse(f.hasSize());
        assertEquals(SIZE, f.getCompressedSize());
        assertFalse(f.hasRelativeHeaderOffset());
        assertFalse(f.hasDiskStartNumber());
        f.clearCompressedSize();
        f.reparseCentralDirectoryData(false, false, true, false);
        assertFalse(f.hasSize());
        assertFalse(f.hasCompressedSize());
        assertEquals(SIZE, f.getRelativeHeaderOffset());
        assertFalse(f.hasDiskStartNumber());
    }

    @Test
    public void testWriteCDOnlySizes() {
        final Zip64ExtendedInformationExtraField f = new Zip64ExtendedInformationExtraField(SIZE, CSIZE);
        assertEquals(16, f.getCentralDirectoryLength());
        final byte[] b = f.getCentralDirectoryData();
        assertEquals(16, b.length);
        checkSizes(b);
    }

    @Test
    public void testWriteCDSizeAndDisk() {
        final Zip64ExtendedInformationExtraField f = new Zip64ExtendedInformationExtraField(SIZE, CSIZE);
        f.setDiskStartNumber(DISK);
        assertEquals(20, f.getCentralDirectoryLength());
        final byte[] b = f.getCentralDirectoryData();
        assertEquals(20, b.length);
        checkSizes(b);
        checkDisk(b, 16);
    }

    @Test
    public void testWriteCDSizeAndOffset() {
        final Zip64ExtendedInformationExtraField f = new Zip64ExtendedInformationExtraField(SIZE, CSIZE);
        f.setRelativeHeaderOffset(OFF);
        assertEquals(24, f.getCentralDirectoryLength());
        final byte[] b = f.getCentralDirectoryData();
        assertEquals(24, b.length);
        checkSizes(b);
        checkOffset(b, 16);
    }

    @Test
    public void testWriteCDSizeOffsetAndDisk() {
        final Zip64ExtendedInformationExtraField f = new Zip64ExtendedInformationExtraField(SIZE, CSIZE, OFF, DISK);
        assertEquals(28, f.getCentralDirectoryLength());
        final byte[] b = f.getCentralDirectoryData();
        assertEquals(28, b.length);
        checkSizes(b);
        checkOffset(b, 16);
        checkDisk(b, 24);
    }
}
