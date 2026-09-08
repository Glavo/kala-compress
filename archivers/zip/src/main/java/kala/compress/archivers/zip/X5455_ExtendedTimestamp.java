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

import kala.compress.utils.ByteUtils;

import java.io.Serializable;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.zip.ZipException;

import kala.compress.utils.TimeUtils;

/**
 * <p>
 * An extra field that stores additional file and directory timestamp data for ZIP entries. Each ZIP entry can include up to three timestamps (modify, access,
 * create*). The timestamps are stored as 32 bit signed integers representing seconds since Unix epoch (Jan 1st, 1970, UTC). This field improves on ZIP's
 * default timestamp granularity, since it allows one to store additional timestamps, and, in addition, the timestamps are stored using per-second granularity
 * (zip's default behavior can only store timestamps to the nearest <em>even</em> second).
 * </p>
 * <p>
 * Unfortunately, 32 (signed) bits can only store dates up to the year 2037, and so this extra field will eventually be obsolete. Enjoy it while it lasts!
 * </p>
 * <ul>
 * <li><strong>modifyTime:</strong> most recent time of file/directory modification (or file/dir creation if the entry has not been modified since it was
 * created).</li>
 * <li><strong>accessTime:</strong> most recent time file/directory was opened (e.g., read from disk). Many people disable their operating systems from updating
 * this value using the NOATIME mount option to optimize disk behavior, and thus it's not always reliable. In those cases it's always equal to modifyTime.</li>
 * <li><strong>*createTime:</strong> modern Linux file systems (e.g., ext2 and newer) do not appear to store a value like this, and so it's usually omitted
 * altogether in the ZIP extra field. Perhaps other Unix systems track this.</li>
 * </ul>
 * <p>
 * We're using the field definition given in Info-Zip's source archive: zip-3.0.tar.gz/proginfo/extrafld.txt
 * </p>
 *
 * <pre>
 * Value         Size        Description
 * -----         ----        -----------
 * 0x5455        Short       tag for this extra block type ("UT")
 * TSize         Short       total data size for this block
 * Flags         Byte        info bits
 * (ModTime)     Long        time of last modification (UTC/GMT)
 * (AcTime)      Long        time of last access (UTC/GMT)
 * (CrTime)      Long        time of original creation (UTC/GMT)
 *
 * Central-header version:
 *
 * Value         Size        Description
 * -----         ----        -----------
 * 0x5455        Short       tag for this extra block type ("UT")
 * TSize         Short       total data size for this block
 * Flags         Byte        info bits (refers to local header!)
 * (ModTime)     Long        time of last modification (UTC/GMT)
 * </pre>
 *
 * @since 1.5
 */
public class X5455_ExtendedTimestamp implements ZipExtraField, Cloneable, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * The header ID for this extra field.
     *
     * @since 1.23
     */
    public static final short HEADER_ID = (short) 0x5455;

    /**
     * The bit set inside the flags by when the last modification time is present in this extra field.
     */
    public static final byte MODIFY_TIME_BIT = 1;
    /**
     * The bit set inside the flags by when the lasr access time is present in this extra field.
     */
    public static final byte ACCESS_TIME_BIT = 2;
    /**
     * The bit set inside the flags by when the original creation time is present in this extra field.
     */
    public static final byte CREATE_TIME_BIT = 4;

    /// Converts a FileTime to signed 32-bit Unix seconds.
    private static int toUnixSeconds(final FileTime time) {
        final long seconds = TimeUtils.toUnixTime(time);
        if (!TimeUtils.isUnixTime(seconds)) {
            throw new IllegalArgumentException("X5455 timestamps must fit in a signed 32 bit integer: " + seconds);
        }
        return (int) seconds;
    }

    /// Identifies timestamp values actually stored, independently of the serialized flags.
    private byte presentFields;

    /// The flags supplied by the caller or read from the archive.
    private byte flags;
    // Note: even if bit1 and bit2 are set, the Central data will still not contain
    // access/create fields: only local data ever holds those! This causes
    // some of our implementation to look a little odd, with seemingly spurious
    // presence and length checks.
    /// Whether the modification time is enabled for serialization.
    private boolean bit0_modifyTimePresent;
    /// Whether the access time is enabled for serialization.
    private boolean bit1_accessTimePresent;

    /// Whether the creation time is enabled for serialization.
    private boolean bit2_createTimePresent;

    /// The signed modification time in Unix seconds.
    private int modifyTime;

    /// The signed access time in Unix seconds.
    private int accessTime;

    /// The signed creation time in Unix seconds.
    private int createTime;

    /**
     * Constructor for X5455_ExtendedTimestamp.
     */
    public X5455_ExtendedTimestamp() {
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof X5455_ExtendedTimestamp) {
            final X5455_ExtendedTimestamp xf = (X5455_ExtendedTimestamp) o;

            // Only the last three flag bits affect equality.
            return (flags & 0x07) == (xf.flags & 0x07) && presentFields == xf.presentFields
                    && (!hasModifyTime() || modifyTime == xf.modifyTime) && (!hasAccessTime() || accessTime == xf.accessTime)
                    && (!hasCreateTime() || createTime == xf.createTime);
        }
        return false;
    }

    /**
     * Gets the access time as a {@link FileTime} of this ZIP entry, or null if no such timestamp exists in the ZIP entry. The milliseconds are always zeroed
     * out, since the underlying data offers only per-second precision.
     *
     * @return modify time as {@link FileTime} or null.
     * @since 1.23
     */
    public FileTime getAccessFileTime() {
        return hasAccessTime() ? TimeUtils.unixTimeToFileTime(accessTime) : null;
    }

    /// Returns the signed Unix timestamp in seconds since 1970-01-01 UTC, or null if absent.
    public Integer getAccessTime() {
        return hasAccessTime() ? accessTime : null;
    }

    /// Returns whether the access timestamp value is stored, independently of the flags.
    public boolean hasAccessTime() {
        return (presentFields & ACCESS_TIME_BIT) != 0;
    }

    /// Clears the access timestamp and its flag.
    public void clearAccessTime() {
        presentFields &= ~ACCESS_TIME_BIT;
        flags &= ~ACCESS_TIME_BIT;
        bit1_accessTimePresent = false;
        accessTime = 0;
    }

    /**
     * Gets the actual data to put into central directory data - without Header-ID or length specifier.
     *
     * @return the central directory data
     */
    @Override
    public byte[] getCentralDirectoryData() {
        // Truncate out create & access time (last 8 bytes) from
        // the copy of the local data we obtained:
        return Arrays.copyOf(getLocalFileDataData(), getCentralDirectoryLength());
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
        return 1 + (bit0_modifyTimePresent && hasModifyTime() ? 4 : 0);
    }

    /**
     * Gets the create time as a {@link FileTime} of this ZIP entry, or null if no such timestamp exists in the ZIP entry. The milliseconds are always zeroed
     * out, since the underlying data offers only per-second precision.
     *
     * @return modify time as {@link FileTime} or null.
     * @since 1.23
     */
    public FileTime getCreateFileTime() {
        return hasCreateTime() ? TimeUtils.unixTimeToFileTime(createTime) : null;
    }

    /// Returns the signed Unix timestamp in seconds since 1970-01-01 UTC, or null if absent.
    public Integer getCreateTime() {
        return hasCreateTime() ? createTime : null;
    }

    /// Returns whether the create timestamp value is stored, independently of the flags.
    public boolean hasCreateTime() {
        return (presentFields & CREATE_TIME_BIT) != 0;
    }

    /// Clears the create timestamp and its flag.
    public void clearCreateTime() {
        presentFields &= ~CREATE_TIME_BIT;
        flags &= ~CREATE_TIME_BIT;
        bit2_createTimePresent = false;
        createTime = 0;
    }

    /**
     * Gets flags byte. The flags byte tells us which of the three datestamp fields are present in the data:
     *
     * <pre>
     * bit0 - modify time
     * bit1 - access time
     * bit2 - create time
     * </pre>
     *
     * Only first 3 bits of flags are used according to the latest version of the spec (December 2012).
     *
     * @return flags byte indicating which of the three datestamp fields are present.
     */
    public byte getFlags() {
        return flags;
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
        int pos = 0;
        data[pos++] = 0;
        if (bit0_modifyTimePresent && hasModifyTime()) {
            data[0] |= MODIFY_TIME_BIT;
            ByteUtils.setIntLE(data, pos, modifyTime);
            pos += 4;
        }
        if (bit1_accessTimePresent && hasAccessTime()) {
            data[0] |= ACCESS_TIME_BIT;
            ByteUtils.setIntLE(data, pos, accessTime);
            pos += 4;
        }
        if (bit2_createTimePresent && hasCreateTime()) {
            data[0] |= CREATE_TIME_BIT;
            ByteUtils.setIntLE(data, pos, createTime);
            pos += 4; // NOSONAR - assignment as documentation
        }
        return data;
    }

    /**
     * Gets the length of the extra field in the local file data - without Header-ID or length specifier.
     *
     * @return the length of the data of this extra field
     */
    @Override
    public int getLocalFileDataLength() {
        return (1 + (bit0_modifyTimePresent && hasModifyTime() ? 4 : 0) + (bit1_accessTimePresent && hasAccessTime() ? 4 : 0)
                + (bit2_createTimePresent && hasCreateTime() ? 4 : 0));
    }

    /**
     * Gets the modify time as a {@link FileTime} of this ZIP entry, or null if no such timestamp exists in the ZIP entry. The milliseconds are always zeroed
     * out, since the underlying data offers only per-second precision.
     *
     * @return modify time as {@link FileTime} or null.
     * @since 1.23
     */
    public FileTime getModifyFileTime() {
        return hasModifyTime() ? TimeUtils.unixTimeToFileTime(modifyTime) : null;
    }

    /// Returns the signed Unix timestamp in seconds since 1970-01-01 UTC, or null if absent.
    public Integer getModifyTime() {
        return hasModifyTime() ? modifyTime : null;
    }

    /// Returns whether the modify timestamp value is stored, independently of the flags.
    public boolean hasModifyTime() {
        return (presentFields & MODIFY_TIME_BIT) != 0;
    }

    /// Clears the modify timestamp and its flag.
    public void clearModifyTime() {
        presentFields &= ~MODIFY_TIME_BIT;
        flags &= ~MODIFY_TIME_BIT;
        bit0_modifyTimePresent = false;
        modifyTime = 0;
    }

    @Override
    public int hashCode() {
        int hc = -123 * (flags & 0x07); // only last 3 bits of flags matter
        if (hasModifyTime()) {
            hc ^= Integer.hashCode(modifyTime);
        }
        if (hasAccessTime()) {
            // Since accessTime is often same as modifyTime,
            // this prevents them from XOR negating each other.
            hc ^= Integer.rotateLeft(Integer.hashCode(accessTime), 11);
        }
        if (hasCreateTime()) {
            hc ^= Integer.rotateLeft(Integer.hashCode(createTime), 22);
        }
        return hc;
    }

    /**
     * Tests whether bit0 of the flags byte is set or not, which should correspond to the presence or absence of a modify timestamp in this particular ZIP
     * entry.
     *
     * @return true if bit0 of the flags byte is set.
     */
    public boolean isBit0_modifyTimePresent() {
        return bit0_modifyTimePresent;
    }

    /**
     * Tests whether bit1 of the flags byte is set or not, which should correspond to the presence or absence of a "last access" timestamp in this particular
     * ZIP entry.
     *
     * @return true if bit1 of the flags byte is set.
     */
    public boolean isBit1_accessTimePresent() {
        return bit1_accessTimePresent;
    }

    /**
     * Tests whether bit2 of the flags byte is set or not, which should correspond to the presence or absence of a create timestamp in this particular ZIP
     * entry.
     *
     * @return true if bit2 of the flags byte is set.
     */
    public boolean isBit2_createTimePresent() {
        return bit2_createTimePresent;
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
        reset();
        if (length < 1) {
            throw new ZipException("X5455_ExtendedTimestamp too short, only " + length + " bytes");
        }
        final int len = offset + length;
        setFlags(data[offset++]);
        if (bit0_modifyTimePresent && offset + 4 <= len) {
            modifyTime = ByteUtils.getIntLE(data, offset);
            presentFields |= MODIFY_TIME_BIT;
            offset += 4;
        } else {
            bit0_modifyTimePresent = false;
        }
        if (bit1_accessTimePresent && offset + 4 <= len) {
            accessTime = ByteUtils.getIntLE(data, offset);
            presentFields |= ACCESS_TIME_BIT;
            offset += 4;
        } else {
            bit1_accessTimePresent = false;
        }
        if (bit2_createTimePresent && offset + 4 <= len) {
            createTime = ByteUtils.getIntLE(data, offset);
            presentFields |= CREATE_TIME_BIT;
            offset += 4; // NOSONAR - assignment as documentation
        } else {
            bit2_createTimePresent = false;
        }
    }

    /**
     * Reset state back to newly constructed state. Helps us make sure parse() calls always generate clean results.
     */
    private void reset() {
        setFlags((byte) 0);
        presentFields = 0;
        this.modifyTime = 0;
        this.accessTime = 0;
        this.createTime = 0;
    }

    /// Sets the access time with second precision, or clears it if null.
    ///
    /// @param time the timestamp, or null
    /// @throws IllegalArgumentException if the Unix seconds do not fit in a signed int
    public void setAccessFileTime(final FileTime time) {
        if (time == null) {
            clearAccessTime();
        } else {
            setAccessTime(toUnixSeconds(time));
        }
    }

    /// Sets the access timestamp and its flag.
    ///
    /// @param time signed seconds since 1970-01-01 UTC
    public void setAccessTime(final int time) {
        accessTime = time;
        presentFields |= ACCESS_TIME_BIT;
        flags |= ACCESS_TIME_BIT;
        bit1_accessTimePresent = true;
    }

    /// Sets the create time with second precision, or clears it if null.
    ///
    /// @param time the timestamp, or null
    /// @throws IllegalArgumentException if the Unix seconds do not fit in a signed int
    public void setCreateFileTime(final FileTime time) {
        if (time == null) {
            clearCreateTime();
        } else {
            setCreateTime(toUnixSeconds(time));
        }
    }

    /// Sets the create timestamp and its flag.
    ///
    /// @param time signed seconds since 1970-01-01 UTC
    public void setCreateTime(final int time) {
        createTime = time;
        presentFields |= CREATE_TIME_BIT;
        flags |= CREATE_TIME_BIT;
        bit2_createTimePresent = true;
    }

    /**
     * Sets flags byte. The flags byte tells us which of the three datestamp fields are present in the data:
     *
     * <pre>
     * bit0 - modify time
     * bit1 - access time
     * bit2 - create time
     * </pre>
     *
     * Only first 3 bits of flags are used according to the latest version of the spec (December 2012).
     *
     * @param flags flags byte indicating which of the three datestamp fields are present.
     */
    public void setFlags(final byte flags) {
        this.flags = flags;
        this.bit0_modifyTimePresent = (flags & MODIFY_TIME_BIT) == MODIFY_TIME_BIT;
        this.bit1_accessTimePresent = (flags & ACCESS_TIME_BIT) == ACCESS_TIME_BIT;
        this.bit2_createTimePresent = (flags & CREATE_TIME_BIT) == CREATE_TIME_BIT;
    }

    /// Sets the modify time with second precision, or clears it if null.
    ///
    /// @param time the timestamp, or null
    /// @throws IllegalArgumentException if the Unix seconds do not fit in a signed int
    public void setModifyFileTime(final FileTime time) {
        if (time == null) {
            clearModifyTime();
        } else {
            setModifyTime(toUnixSeconds(time));
        }
    }

    /// Sets the modify timestamp and its flag.
    ///
    /// @param time signed seconds since 1970-01-01 UTC
    public void setModifyTime(final int time) {
        modifyTime = time;
        presentFields |= MODIFY_TIME_BIT;
        flags |= MODIFY_TIME_BIT;
        bit0_modifyTimePresent = true;
    }

    /**
     * Returns a String representation of this class useful for debugging purposes.
     *
     * @return A String representation of this class useful for debugging purposes.
     */
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append("0x5455 Zip Extra Field: Flags=");
        buf.append(Integer.toBinaryString(ZipUtil.unsignedIntToSignedByte(flags))).append(" ");
        if (bit0_modifyTimePresent && hasModifyTime()) {
            buf.append(" Modify:[").append(getModifyFileTime()).append("] ");
        }
        if (bit1_accessTimePresent && hasAccessTime()) {
            buf.append(" Access:[").append(getAccessFileTime()).append("] ");
        }
        if (bit2_createTimePresent && hasCreateTime()) {
            buf.append(" Create:[").append(getCreateFileTime()).append("] ");
        }
        return buf.toString();
    }

}
