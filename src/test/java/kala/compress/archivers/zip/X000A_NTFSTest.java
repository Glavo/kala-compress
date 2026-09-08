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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Arrays;
import java.util.zip.ZipEntry;

import org.junit.jupiter.api.Test;

public class X000A_NTFSTest {

    /// Encodes absent timestamps so JDK readers leave them unset.
    @Test
    public void testMissingTimes() {
        final X000A_NTFS field = new X000A_NTFS();
        field.setAccessFileTime(FileTime.from(Instant.parse("2024-03-04T12:34:56Z")));
        field.setAccessFileTime(null);
        assertEquals(Long.MIN_VALUE, field.getModifyTime());
        assertEquals(Long.MIN_VALUE, field.getAccessTime());
        assertEquals(Long.MIN_VALUE, field.getCreateTime());
        final ZipArchiveEntry holder = new ZipArchiveEntry("entry");
        holder.addExtraField(field);
        final ZipEntry jdk = new ZipEntry("entry");
        jdk.setExtra(holder.getExtra());
        assertNull(jdk.getLastModifiedTime());
        assertNull(jdk.getLastAccessTime());
        assertNull(jdk.getCreationTime());
    }

    /// Retains support for zero-valued missing timestamps in older archives.
    @Test
    public void testLegacyMissingTimes() throws Exception {
        final X000A_NTFS field = new X000A_NTFS();
        final byte[] data = field.getLocalFileDataData();
        Arrays.fill(data, 8, data.length, (byte) 0);
        field.parseFromLocalFileData(data, 0, data.length);
        assertNull(field.getModifyFileTime());
        assertNull(field.getAccessFileTime());
        assertNull(field.getCreateFileTime());
    }

    @Test
    public void testSimpleRoundtrip() throws Exception {
        final X000A_NTFS xf = new X000A_NTFS();
        xf.setModifyFileTime(FileTime.fromMillis(0));
        // one second past midnight
        xf.setAccessFileTime(FileTime.fromMillis(-11644473601000L));
        xf.setCreateFileTime(null);
        final byte[] b = xf.getLocalFileDataData();

        final X000A_NTFS xf2 = new X000A_NTFS();
        xf2.parseFromLocalFileData(b, 0, b.length);
        assertEquals(FileTime.fromMillis(0), xf2.getModifyFileTime());
        assertEquals(FileTime.fromMillis(-11644473601000L), xf2.getAccessFileTime());
        assertNull(xf2.getCreateFileTime());
    }

    @Test
    public void testSimpleRoundtripWithHighPrecisionDatesWithBigValues() throws Exception {
        final X000A_NTFS xf = new X000A_NTFS();
        xf.setModifyFileTime(FileTime.from(Instant.ofEpochSecond(123456789101L, 123456700)));
        // one second past midnight
        xf.setAccessFileTime(FileTime.from(Instant.ofEpochSecond(-11644473601L)));
        // 765432100ns past midnight
        xf.setCreateFileTime(FileTime.from(Instant.ofEpochSecond(-11644473600L, 765432100)));
        final byte[] b = xf.getLocalFileDataData();

        final X000A_NTFS xf2 = new X000A_NTFS();
        xf2.parseFromLocalFileData(b, 0, b.length);
        assertEquals(FileTime.from(Instant.ofEpochSecond(123456789101L, 123456700)), xf2.getModifyFileTime());
        assertEquals(FileTime.from(Instant.ofEpochSecond(-11644473601L)), xf2.getAccessFileTime());
        assertEquals(FileTime.from(Instant.ofEpochSecond(-11644473600L, 765432100)), xf2.getCreateFileTime());
    }

    @Test
    public void testSimpleRoundtripWithHighPrecisionDatesWithSmallValues() throws Exception {
        final X000A_NTFS xf = new X000A_NTFS();
        // The last 2 digits should not be written due to the 100ns precision
        xf.setModifyFileTime(FileTime.from(Instant.ofEpochSecond(0, 1234)));
        // one second past midnight
        xf.setAccessFileTime(FileTime.from(Instant.ofEpochSecond(-11644473601L)));
        xf.setCreateFileTime(null);
        final byte[] b = xf.getLocalFileDataData();

        final X000A_NTFS xf2 = new X000A_NTFS();
        xf2.parseFromLocalFileData(b, 0, b.length);
        assertEquals(FileTime.from(Instant.ofEpochSecond(0, 1200)), xf2.getModifyFileTime());
        assertEquals(FileTime.from(Instant.ofEpochSecond(-11644473601L)), xf2.getAccessFileTime());
        assertNull(xf2.getCreateFileTime());
    }
}
