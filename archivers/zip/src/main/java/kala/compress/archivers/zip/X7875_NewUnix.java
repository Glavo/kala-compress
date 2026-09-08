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

import java.io.Serializable;
import java.util.zip.ZipException;

import kala.compress.utils.ByteUtils;

/**
 * An extra field that stores Unix UID/GID data (owner &amp; group ownership) for a given ZIP entry. We're using the field definition given in Info-Zip's source
 * archive: zip-3.0.tar.gz/proginfo/extrafld.txt
 *
 * <pre>
 * Local-header version:
 *
 * Value         Size        Description
 * -----         ----        -----------
 * 0x7875        Short       tag for this extra block type ("ux")
 * TSize         Short       total data size for this block
 * Version       1 byte      version of this extra field, currently 1
 * UIDSize       1 byte      Size of UID field
 * UID           Variable    UID for this entry (little-endian)
 * GIDSize       1 byte      Size of GID field
 * GID           Variable    GID for this entry (little-endian)
 *
 * Central-header version:
 *
 * Value         Size        Description
 * -----         ----        -----------
 * 0x7855        Short       tag for this extra block type ("Ux")
 * TSize         Short       total data size for this block (0)
 * </pre>
 *
 * @since 1.5
 */
public class X7875_NewUnix implements ZipExtraField, Cloneable, Serializable {
    /// The Info-ZIP Unix UID/GID extra field identifier.
    static final short HEADER_ID = (short) 0x7875;
    /// The length of the empty central directory data.
    private static final int ZERO = 0;
    /// The default UID and GID.
    private static final long ONE_THOUSAND = 1000;
    /// The serialization version.
    private static final long serialVersionUID = 1L;

    /// The format version, initially 1.
    private int version = 1;

    /// The nonnegative user identifier.
    private long uid;
    /// The nonnegative group identifier.
    private long gid;

    /**
     * Constructor for X7875_NewUnix.
     */
    public X7875_NewUnix() {
        reset();
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof X7875_NewUnix xf) {
            return version == xf.version && uid == xf.uid && gid == xf.gid;
        }
        return false;
    }

    /**
     * The actual data to put into central directory data - without Header-ID or length specifier.
     *
     * @return get the data
     */
    @Override
    public byte[] getCentralDirectoryData() {
        return ByteUtils.EMPTY_BYTE_ARRAY;
    }

    /**
     * Length of the extra field in the central directory data - without Header-ID or length specifier.
     *
     * @return the length of the data of this extra field
     */
    @Override
    public int getCentralDirectoryLength() {
        return ZERO;
    }

    /**
     * Gets the GID as a long. GID is typically a 32 bit unsigned value on most Unix systems, so we return a long to avoid integer overflow into the negatives
     * in case values above and including 2^31 are being used.
     *
     * @return the GID value.
     */
    public long getGID() {
        return gid;
    }

    /**
     * The Header-ID.
     *
     * @return the value for the header id for this extrafield
     */
    @Override
    public short getHeaderId() {
        return HEADER_ID;
    }

    /**
     * The actual data to put into local file data - without Header-ID or length specifier.
     *
     * @return get the data
     */
    @Override
    public byte[] getLocalFileDataData() {
        final int uidBytesLen = getValueLength(uid);
        final int gidBytesLen = getValueLength(gid);
        final byte[] data = new byte[3 + uidBytesLen + gidBytesLen];
        data[0] = (byte) version;
        data[1] = (byte) uidBytesLen;
        ByteUtils.toLittleEndian(data, uid, 2, uidBytesLen);
        data[2 + uidBytesLen] = (byte) gidBytesLen;
        ByteUtils.toLittleEndian(data, gid, 3 + uidBytesLen, gidBytesLen);
        return data;
    }

    /**
     * Length of the extra field in the local file data - without Header-ID or length specifier.
     *
     * @return the length of the data of this extra field
     */
    @Override
    public int getLocalFileDataLength() {
        return 3 + getValueLength(uid) + getValueLength(gid);
    }

    /// Returns the minimum unsigned byte length, using one byte for zero.
    private static int getValueLength(final long value) {
        return Math.max(1, (Long.SIZE - Long.numberOfLeadingZeros(value) + 7) / Byte.SIZE);
    }

    /**
     * Gets the UID as a long. UID is typically a 32 bit unsigned value on most Unix systems, so we return a long to avoid integer overflow into the negatives
     * in case values above and including 2^31 are being used.
     *
     * @return the UID value.
     */
    public long getUID() {
        return uid;
    }

    @Override
    public int hashCode() {
        int hc = -1234567 * version;
        // Since most UIDs and GIDs are below 65,536, this is (hopefully!)
        // a nice way to make sure typical UID and GID values impact the hash
        // as much as possible.
        hc ^= Integer.rotateLeft(Long.hashCode(uid), 16);
        hc ^= Long.hashCode(gid);
        return hc;
    }

    /**
     * Doesn't do anything since this class doesn't store anything inside the central directory.
     */
    @Override
    public void parseFromCentralDirectoryData(final byte[] buffer, final int offset, final int length) throws ZipException {
    }

    /// Parses local UID/GID data. Empty values represent zero; high-order zero padding is accepted.
    ///
    /// @param data the source array
    /// @param offset the start offset
    /// @param length the number of bytes to parse
    /// @throws ZipException if the field is truncated or a UID or GID exceeds [Long#MAX_VALUE]
    @Override
    public void parseFromLocalFileData(final byte[] data, int offset, final int length) throws ZipException {
        reset();
        if (length < 3) {
            throw new ZipException("X7875_NewUnix length is too short, only " + length + " bytes");
        }
        final byte b2 = data[offset++];
        this.version = Byte.toUnsignedInt(b2);
        final byte b1 = data[offset++];
        final int uidSize = Byte.toUnsignedInt(b1);
        if (uidSize + 3 > length) {
            throw new ZipException("X7875_NewUnix invalid: uidSize " + uidSize + " doesn't fit into " + length + " bytes");
        }
        this.uid = readValue(data, offset, uidSize);
        offset += uidSize;

        final byte b = data[offset++];
        final int gidSize = Byte.toUnsignedInt(b);
        if (uidSize + 3 + gidSize > length) {
            throw new ZipException("X7875_NewUnix invalid: gidSize " + gidSize + " doesn't fit into " + length + " bytes");
        }
        this.gid = readValue(data, offset, gidSize);
    }

    /// Reads a nonnegative identifier, rejecting values that cannot fit in a long.
    private static long readValue(final byte[] data, final int offset, int length) throws ZipException {
        while (length > 0 && data[offset + length - 1] == 0) {
            length--;
        }
        if (length > Long.BYTES || length == Long.BYTES && data[offset + length - 1] < 0) {
            throw new ZipException("X7875_NewUnix UID/GID exceeds Long.MAX_VALUE");
        }
        return ByteUtils.fromLittleEndian(data, offset, length);
    }

    /// Interprets negative int values as unsigned 32-bit identifiers.
    private static long normalizeValue(final long value) {
        if (value < Integer.MIN_VALUE) {
            throw new IllegalArgumentException("Negative longs < -2^31 not permitted: [" + value + "]");
        }
        return value < 0 ? Integer.toUnsignedLong((int) value) : value;
    }

    /**
     * Reset state back to newly constructed state. Helps us make sure parse() calls always generate clean results.
     */
    private void reset() {
        // Typical UID/GID of the first non-root user created on a Unix system.
        uid = ONE_THOUSAND;
        gid = ONE_THOUSAND;
    }

    /// Sets the GID, interpreting negative int values as unsigned 32-bit identifiers.
    ///
    /// @param l the GID
    /// @throws IllegalArgumentException if l is less than [Integer#MIN_VALUE]
    public void setGID(final long l) {
        this.gid = normalizeValue(l);
    }

    /// Sets the UID, interpreting negative int values as unsigned 32-bit identifiers.
    ///
    /// @param l the UID
    /// @throws IllegalArgumentException if l is less than [Integer#MIN_VALUE]
    public void setUID(final long l) {
        this.uid = normalizeValue(l);
    }

    /**
     * Returns a String representation of this class useful for debugging purposes.
     *
     * @return A String representation of this class useful for debugging purposes.
     */
    @Override
    public String toString() {
        return "0x7875 Zip Extra Field: UID=" + uid + " GID=" + gid;
    }
}
