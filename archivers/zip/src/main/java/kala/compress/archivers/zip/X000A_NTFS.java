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
import kala.compress.utils.TimeUtils;

import java.nio.file.attribute.FileTime;
import java.util.zip.ZipException;

/**
 * NTFS extra field that was thought to store various attributes but in reality only stores timestamps.
 *
 * <pre>
 *    4.5.5 -NTFS Extra Field (0x000a):
 *
 *       The following is the layout of the NTFS attributes
 *       "extra" block. (Note: At this time the Mtime, Atime
 *       and Ctime values MAY be used on any WIN32 system.)
 *
 *       Note: all fields stored in Intel low-byte/high-byte order.
 *
 *         Value      Size       Description
 *         -----      ----       -----------
 * (NTFS)  0x000a     2 bytes    Tag for this "extra" block type
 *         TSize      2 bytes    Size of the total "extra" block
 *         Reserved   4 bytes    Reserved for future use
 *         Tag1       2 bytes    NTFS attribute tag value #1
 *         Size1      2 bytes    Size of attribute #1, in bytes
 *         (var)      Size1      Attribute #1 data
 *          .
 *          .
 *          .
 *          TagN       2 bytes    NTFS attribute tag value #N
 *          SizeN      2 bytes    Size of attribute #N, in bytes
 *          (var)      SizeN      Attribute #N data
 *
 *        For NTFS, values for Tag1 through TagN are as follows:
 *        (currently only one set of attributes is defined for NTFS)
 *
 *          Tag        Size       Description
 *          -----      ----       -----------
 *          0x0001     2 bytes    Tag for attribute #1
 *          Size1      2 bytes    Size of attribute #1, in bytes
 *          Mtime      8 bytes    File last modification time
 *          Atime      8 bytes    File last access time
 *          Ctime      8 bytes    File creation time
 * </pre>
 *
 * @NotThreadSafe
 * @since 1.11
 */
public class X000A_NTFS implements ZipExtraField {

    /**
     * The header ID for this extra field.
     *
     * @since 1.23
     */
    public static final short HEADER_ID = (short) 0x000a;

    private static final short TIME_ATTR_TAG = (short) 0x0001;
    private static final int TIME_ATTR_SIZE = 3 * 8;

    /// The missing-timestamp marker recognized by java.util.zip.
    private static final long TIME_NOT_AVAILABLE = (Long.MIN_VALUE);

    /// Encodes a timestamp, using the missing-time marker for null.
    private static long fileTimeToZip(final FileTime time) {
        if (time == null) {
            return TIME_NOT_AVAILABLE;
        }
        return TimeUtils.toNtfsTime(time);
    }

    /// Decodes a timestamp, accepting both the JDK marker and legacy zero values as absent.
    private static FileTime zipToFileTime(final long z) {
        if (z == TIME_NOT_AVAILABLE || z == 0) {
            return null;
        }
        return TimeUtils.ntfsTimeToFileTime(z);
    }

    /// The encoded modification time or a missing-time marker.
    private long modifyTime = TIME_NOT_AVAILABLE;

    /// The encoded access time or a missing-time marker.
    private long accessTime = TIME_NOT_AVAILABLE;

    /// The encoded creation time or a missing-time marker.
    private long createTime = TIME_NOT_AVAILABLE;

    @Override
    public boolean equals(final Object o) {
        if (o instanceof X000A_NTFS) {
            final X000A_NTFS xf = (X000A_NTFS) o;

            return modifyTime == xf.modifyTime && accessTime == xf.accessTime && createTime == xf.createTime;
        }
        return false;
    }

    /**
     * Gets the access time as a {@link FileTime} of this ZIP entry, or null if no such timestamp exists in the ZIP entry.
     *
     * @return access time as a {@link FileTime} or null.
     * @since 1.23
     */
    public FileTime getAccessFileTime() {
        return zipToFileTime(accessTime);
    }

    /// Returns the encoded access time in 100-nanosecond units since 1601-01-01 UTC.
    /// Unset times use [Long#MIN_VALUE]; a parsed zero is also treated as absent.
    public long getAccessTime() {
        return accessTime;
    }

    /**
     * Gets the actual data to put into central directory data - without Header-ID or length specifier.
     *
     * @return the central directory data
     */
    @Override
    public byte[] getCentralDirectoryData() {
        return getLocalFileDataData();
    }

    /**
     * Gets the length of the extra field in the local file data - without Header-ID or length specifier.
     *
     * <p>
     * For X5455 the central length is often smaller than the local length, because central cannot contain access or create timestamps.
     * </p>
     *
     * @return the length of the data of this extra field
     */
    @Override
    public int getCentralDirectoryLength() {
        return getLocalFileDataLength();
    }

    /**
     * Gets the create time as a {@link FileTime} of this ZIP entry, or null if no such timestamp exists in the ZIP entry.
     *
     * @return create time as a {@link FileTime} or null.
     * @since 1.23
     */
    public FileTime getCreateFileTime() {
        return zipToFileTime(createTime);
    }

    /// Returns the encoded creation time in 100-nanosecond units since 1601-01-01 UTC.
    /// Unset times use [Long#MIN_VALUE]; a parsed zero is also treated as absent.
    public long getCreateTime() {
        return createTime;
    }

    /**
     * Gets the Header-ID.
     *
     * @return the value for the header id for this extrafield
     */
    @Override
    public short getHeaderId() {
        return HEADER_ID;
    }

    /**
     * Gets the actual data to put into local file data - without Header-ID or length specifier.
     *
     * @return get the data
     */
    @Override
    public byte[] getLocalFileDataData() {
        final byte[] data = new byte[getLocalFileDataLength()];
        int pos = 4;
        ByteUtils.setUnsignedShortLE(data, pos, TIME_ATTR_TAG);
        pos += 2;
        ByteUtils.setUnsignedShortLE(data, pos, TIME_ATTR_SIZE);
        pos += 2;
        ByteUtils.setLongLE(data, pos, modifyTime);
        pos += 8;
        ByteUtils.setLongLE(data, pos, accessTime);
        pos += 8;
        ByteUtils.setLongLE(data, pos, createTime);
        return data;
    }

    private static final int LOCAL_FILE_DATA_LENGTH = 4 /* reserved */
            + 2 /* Tag#1 */
            + 2 /* Size#1 */
            + 3 * 8 /* time values */;

    /**
     * Gets the length of the extra field in the local file data - without Header-ID or length specifier.
     *
     * @return the length of the data of this extra field
     */
    @Override
    public int getLocalFileDataLength() {
        return LOCAL_FILE_DATA_LENGTH;
    }

    /**
     * Gets the modify time as a {@link FileTime} of this ZIP entry, or null if no such timestamp exists in the ZIP entry.
     *
     * @return modify time as a {@link FileTime} or null.
     * @since 1.23
     */
    public FileTime getModifyFileTime() {
        return zipToFileTime(modifyTime);
    }

    /// Returns the encoded modification time in 100-nanosecond units since 1601-01-01 UTC.
    /// Unset times use [Long#MIN_VALUE]; a parsed zero is also treated as absent.
    public long getModifyTime() {
        return modifyTime;
    }

    @Override
    public int hashCode() {
        int hc = -123;
        hc ^= Long.hashCode(modifyTime);
        // Since accessTime is often same as modifyTime,
        // this prevents them from XOR negating each other.
        hc ^= Integer.rotateLeft(Long.hashCode(accessTime), 11);
        hc ^= Integer.rotateLeft(Long.hashCode(createTime), 22);
        return hc;
    }

    /**
     * Doesn't do anything special since this class always uses the same parsing logic for both central directory and local file data.
     */
    @Override
    public void parseFromCentralDirectoryData(final byte[] buffer, final int offset, final int length) throws ZipException {
        reset();
        parseFromLocalFileData(buffer, offset, length);
    }

    /**
     * Populate data from this array as if it was in local file data.
     *
     * @param data   an array of bytes
     * @param offset the start offset
     * @param length the number of bytes in the array from offset
     * @throws ZipException on error
     */
    @Override
    public void parseFromLocalFileData(final byte[] data, int offset, final int length) throws ZipException {
        final int len = offset + length;

        // skip reserved
        offset += 4;

        while (offset + 4 <= len) {
            final short tag = ByteUtils.getShortLE(data, offset);
            offset += 2;
            if (tag == TIME_ATTR_TAG) {
                readTimeAttr(data, offset, len - offset);
                break;
            }
            final int size = ByteUtils.getUnsignedShortLE(data, offset);
            offset += 2 + size;
        }
    }

    private void readTimeAttr(final byte[] data, int offset, final int length) {
        if (length >= 2 + 3 * 8) {
            final int tagValueLength = ByteUtils.getUnsignedShortLE(data, offset);
            if (TIME_ATTR_SIZE == tagValueLength) {
                offset += 2;
                modifyTime = ByteUtils.getLongLE(data, offset);
                offset += 8;
                accessTime = ByteUtils.getLongLE(data, offset);
                offset += 8;
                createTime = ByteUtils.getLongLE(data, offset);
            }
        }
    }

    /**
     * Reset state back to newly constructed state. Helps us make sure parse() calls always generate clean results.
     */
    private void reset() {
        this.modifyTime = TIME_NOT_AVAILABLE;
        this.accessTime = TIME_NOT_AVAILABLE;
        this.createTime = TIME_NOT_AVAILABLE;
    }

    /**
     * Sets the access time.
     *
     * @param time access time as a {@link FileTime}
     * @since 1.23
     */
    public void setAccessFileTime(final FileTime time) {
        setAccessTime(fileTimeToZip(time));
    }

    /// Sets the encoded access time.
    ///
    /// @param t the timestamp in 100-nanosecond units since 1601-01-01 UTC, or [Long#MIN_VALUE] to clear it
    public void setAccessTime(final long t) {
        accessTime = t;
    }

    /**
     * Sets the create time.
     *
     * @param time create time as a {@link FileTime}
     * @since 1.23
     */
    public void setCreateFileTime(final FileTime time) {
        setCreateTime(fileTimeToZip(time));
    }

    /// Sets the encoded creation time.
    ///
    /// @param t the timestamp in 100-nanosecond units since 1601-01-01 UTC, or [Long#MIN_VALUE] to clear it
    public void setCreateTime(final long t) {
        createTime = t;
    }

    /**
     * Sets the modify time.
     *
     * @param time modify time as a {@link FileTime}
     * @since 1.23
     */
    public void setModifyFileTime(final FileTime time) {
        setModifyTime(fileTimeToZip(time));
    }

    /// Sets the encoded modification time.
    ///
    /// @param t the timestamp in 100-nanosecond units since 1601-01-01 UTC, or [Long#MIN_VALUE] to clear it
    public void setModifyTime(final long t) {
        modifyTime = t;
    }

    /**
     * Returns a String representation of this class useful for debugging purposes.
     *
     * @return A String representation of this class useful for debugging purposes.
     */
    @Override
    public String toString() {
        // @formatter:off
        return new StringBuilder()
            .append("0x000A Zip Extra Field:")
            .append(" Modify:[")
            .append(getModifyFileTime())
            .append("] ")
            .append(" Access:[")
            .append(getAccessFileTime())
            .append("] ")
            .append(" Create:[")
            .append(getCreateFileTime())
            .append("] ")
            .toString();
        // @formatter:on
    }
}
