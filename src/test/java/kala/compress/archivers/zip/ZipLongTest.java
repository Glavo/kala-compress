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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;

/**
 * JUnit testcases for kala.compress.archivers.zip.ZipLong.
 *
 */
public class ZipLongTest {

    /**
     * Test conversion to bytes.
     */
    @Test
    public void testToBytes() {
        final ZipLong zl = ZipLong.valueOf(0x12345678);
        final byte[] result = zl.getBytes();
        assertEquals("length getBytes", 4, result.length);
        assertEquals("first byte getBytes", 0x78, result[0]);
        assertEquals("second byte getBytes", 0x56, result[1]);
        assertEquals("third byte getBytes", 0x34, result[2]);
        assertEquals("fourth byte getBytes", 0x12, result[3]);
    }

    /**
     * Test conversion to bytes.
     */
    @Test
    public void testPut() {
        final byte[] arr = new byte[5];
        ZipLong.putLong(0x12345678, arr, 1);
        assertEquals("first byte getBytes", 0x78, arr[1]);
        assertEquals("second byte getBytes", 0x56, arr[2]);
        assertEquals("third byte getBytes", 0x34, arr[3]);
        assertEquals("fourth byte getBytes", 0x12, arr[4]);
    }

    /**
     * Test conversion from bytes.
     */
    @Test
    public void testFromBytes() {
        final byte[] val = new byte[] {0x78, 0x56, 0x34, 0x12};
        final ZipLong zl = ZipLong.valueOf(val);
        assertEquals("value from bytes", 0x12345678, zl.getValue());
    }

    /**
     * Test the contract of the equals method.
     */
    @Test
    public void testEquals() {
        final ZipLong zl = ZipLong.valueOf(0x12345678);
        final ZipLong zl2 = ZipLong.valueOf(0x12345678);
        final ZipLong zl3 = ZipLong.valueOf(0x87654321);

        assertEquals("reflexive", zl, zl);

        assertEquals("works", zl, zl2);
        assertNotEquals("works, part two", zl, zl3);

        assertEquals("symmetric", zl2, zl);

        assertNotEquals("null handling", null, zl);
        assertNotEquals("non ZipLong handling", zl, 0x1234);
    }

    /**
     * Test sign handling.
     */
    @Test
    public void testSign() {
         ZipLong zl = ZipLong.valueOf(new byte[] {(byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF});
        assertEquals(0x00000000FFFFFFFFL, zl.getValue());
        assertEquals(-1,zl.getIntValue());

        zl = ZipLong.valueOf(0xFFFF_FFFFL);
        assertEquals(0x00000000FFFFFFFFL, zl.getValue());
        zl = ZipLong.valueOf(0xFFFF_FFFF);
        assertEquals(0xFFFF_FFFF_FFFF_FFFFL, zl.getValue());

    }
}
