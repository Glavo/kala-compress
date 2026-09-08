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

import static kala.compress.archivers.zip.ZipConstants.DWORD;
import static kala.compress.archivers.zip.ZipConstants.WORD;

import java.util.zip.ZipException;

import kala.compress.utils.ByteUtils;

/**
 * Holds size and other extended information for entries that use Zip64 features.
 *
 * <p>
 * Currently Commons Compress doesn't support encrypting the central directory so the note in APPNOTE.TXT about masking doesn't apply.
 * </p>
 *
 * <p>
 * The implementation relies on data being read from the local file header and assumes that both size values are always present.
 * </p>
 *
 * @see <a href="https://www.pkware.com/documents/casestudies/APPNOTE.TXT">PKWARE APPNOTE.TXT, section 4.5.3</a>
 *
 * @since 1.2
 * @NotThreadSafe
 */
public class Zip64ExtendedInformationExtraField implements ZipExtraField {

    static final short HEADER_ID = (short) 0x0001;

    private static final String LFH_MUST_HAVE_BOTH_SIZES_MSG = "Zip64 extended information must contain" + " both size values in the local file header.";
    /// The optional fields present in this extra field.
    private int presentFields;
    /// The raw unsigned 64-bit uncompressed size.
    private long size;
    /// The raw unsigned 64-bit compressed size.
    private long compressedSize;
    /// The raw unsigned 64-bit local header offset.
    private long relativeHeaderOffset;
    /// The raw unsigned 32-bit starting disk number.
    private int diskStart;

    /**
     * Stored in {@link #parseFromCentralDirectoryData parseFromCentralDirectoryData} so it can be reused when ZipFile calls {@link #reparseCentralDirectoryData
     * reparseCentralDirectoryData}.
     *
     * <p>
     * Not used for anything else
     * </p>
     *
     * @since 1.3
     */
    private byte[] rawCentralDirectoryData;

    /**
     * This constructor should only be used by the code that reads archives inside of Commons Compress.
     */
    public Zip64ExtendedInformationExtraField() {
    }

    /**
     * Creates an extra field based on the original and compressed size.
     *
     * @param size           the entry's original size
     * @param compressedSize the entry's compressed size
     */
    public Zip64ExtendedInformationExtraField(final long size, final long compressedSize) {
        setSize(size);
        setCompressedSize(compressedSize);
    }

    /**
     * Creates an extra field based on all four possible values.
     *
     * @param size                 the entry's original size
     * @param compressedSize       the entry's compressed size
     * @param relativeHeaderOffset the entry's offset
     * @param diskStart            the disk start
     */
    public Zip64ExtendedInformationExtraField(final long size, final long compressedSize,
            final long relativeHeaderOffset, final int diskStart) {
        setSize(size);
        setCompressedSize(compressedSize);
        setRelativeHeaderOffset(relativeHeaderOffset);
        setDiskStartNumber(diskStart);
    }

    private int addSizes(final byte[] data) {
        int off = 0;
        if (hasSize()) {
            ByteUtils.setLongLE(data, 0, size);
            off += DWORD;
        }
        if (hasCompressedSize()) {
            ByteUtils.setLongLE(data, off, compressedSize);
            off += DWORD;
        }
        return off;
    }

    @Override
    public byte[] getCentralDirectoryData() {
        final byte[] data = new byte[getCentralDirectoryLength()];
        int off = addSizes(data);
        if (hasRelativeHeaderOffset()) {
            ByteUtils.setLongLE(data, off, relativeHeaderOffset);
            off += DWORD;
        }
        if (hasDiskStartNumber()) {
            ByteUtils.setIntLE(data, off, diskStart);
            off += WORD; // NOSONAR - assignment as documentation
        }
        return data;
    }

    @Override
    public int getCentralDirectoryLength() {
        return ((hasSize() ? DWORD : 0) + (hasCompressedSize() ? DWORD : 0) + (hasRelativeHeaderOffset() ? DWORD : 0)
                + (hasDiskStartNumber() ? WORD : 0));
    }

    /// Returns the unsigned 64-bit compressed size as a raw bit pattern, or null if absent.
    public Long getCompressedSize() {
        return hasCompressedSize() ? compressedSize : null;
    }

    /// Returns the unsigned 32-bit starting disk number as a raw bit pattern, or null if absent.
    public Integer getDiskStartNumber() {
        return hasDiskStartNumber() ? diskStart : null;
    }

    @Override
    public short getHeaderId() {
        return HEADER_ID;
    }

    /// Returns local data containing both sizes, or an empty array if neither size is present.
    ///
    /// @throws IllegalArgumentException if exactly one size is present
    @Override
    public byte[] getLocalFileDataData() {
        if (hasSize() || hasCompressedSize()) {
            if (!hasSize() || !hasCompressedSize()) {
                throw new IllegalArgumentException(LFH_MUST_HAVE_BOTH_SIZES_MSG);
            }
            final byte[] data = new byte[2 * DWORD];
            addSizes(data);
            return data;
        }
        return ByteUtils.EMPTY_BYTE_ARRAY;
    }

    /// Returns 16 if both sizes are present, or zero if neither is present.
    ///
    /// @throws IllegalArgumentException if exactly one size is present
    @Override
    public int getLocalFileDataLength() {
        if (hasSize() != hasCompressedSize()) {
            throw new IllegalArgumentException(LFH_MUST_HAVE_BOTH_SIZES_MSG);
        }
        return hasSize() ? 2 * DWORD : 0;
    }

    /// Returns the unsigned 64-bit local header offset as a raw bit pattern, or null if absent.
    public Long getRelativeHeaderOffset() {
        return hasRelativeHeaderOffset() ? relativeHeaderOffset : null;
    }

    /// Returns the unsigned 64-bit uncompressed size as a raw bit pattern, or null if absent.
    public Long getSize() {
        return hasSize() ? size : null;
    }

    @Override
    public void parseFromCentralDirectoryData(final byte[] buffer, int offset, final int length) throws ZipException {
        // store for processing in reparseCentralDirectoryData
        rawCentralDirectoryData = new byte[length];
        System.arraycopy(buffer, offset, rawCentralDirectoryData, 0, length);

        // if there is no size information in here, we are screwed and
        // can only hope things will get resolved by LFH data later
        // But there are some cases that can be detected
        // * all data is there
        // * length == 24 -> both sizes and offset
        // * length % 8 == 4 -> at least we can identify the diskStart field
        if (length >= 3 * DWORD + WORD) {
            parseFromLocalFileData(buffer, offset, length);
        } else if (length == 3 * DWORD) {
            setSize(ByteUtils.getLongLE(buffer, offset));
            offset += DWORD;
            setCompressedSize(ByteUtils.getLongLE(buffer, offset));
            offset += DWORD;
            setRelativeHeaderOffset(ByteUtils.getLongLE(buffer, offset));
        } else if (length % DWORD == WORD) {
            setDiskStartNumber(ByteUtils.getIntLE(buffer, offset + length - WORD));
        }
    }

    @Override
    public void parseFromLocalFileData(final byte[] buffer, int offset, final int length) throws ZipException {
        if (length == 0) {
            // no local file data at all, may happen if an archive
            // only holds a ZIP64 extended information extra field
            // inside the central directory but not inside the local
            // file header
            return;
        }
        if (length < 2 * DWORD) {
            throw new ZipException(LFH_MUST_HAVE_BOTH_SIZES_MSG);
        }
        setSize(ByteUtils.getLongLE(buffer, offset));
        offset += DWORD;
        setCompressedSize(ByteUtils.getLongLE(buffer, offset));
        offset += DWORD;
        int remaining = length - 2 * DWORD;
        if (remaining >= DWORD) {
            setRelativeHeaderOffset(ByteUtils.getLongLE(buffer, offset));
            offset += DWORD;
            remaining -= DWORD;
        }
        if (remaining >= WORD) {
            setDiskStartNumber(ByteUtils.getIntLE(buffer, offset));
            offset += WORD; // NOSONAR - assignment as documentation
            remaining -= WORD; // NOSONAR - assignment as documentation
        }
    }

    /**
     * Parses the raw bytes read from the central directory extra field with knowledge which fields are expected to be there.
     *
     * <p>
     * All four fields inside the zip64 extended information extra field are optional and must only be present if their corresponding entry inside the central
     * directory contains the correct magic value.
     * </p>
     *
     * @param hasUncompressedSize     flag to read from central directory
     * @param hasCompressedSize       flag to read from central directory
     * @param hasRelativeHeaderOffset flag to read from central directory
     * @param hasDiskStart            flag to read from central directory
     * @throws ZipException on error
     */
    public void reparseCentralDirectoryData(final boolean hasUncompressedSize, final boolean hasCompressedSize, final boolean hasRelativeHeaderOffset,
            final boolean hasDiskStart) throws ZipException {
        if (rawCentralDirectoryData != null) {
            final int expectedLength = (hasUncompressedSize ? DWORD : 0) + (hasCompressedSize ? DWORD : 0) + (hasRelativeHeaderOffset ? DWORD : 0)
                    + (hasDiskStart ? WORD : 0);
            if (rawCentralDirectoryData.length < expectedLength) {
                throw new ZipException("Central directory zip64 extended" + " information extra field's length" + " doesn't match central directory"
                        + " data.  Expected length " + expectedLength + " but is " + rawCentralDirectoryData.length);
            }
            int offset = 0;
            if (hasUncompressedSize) {
                setSize(ByteUtils.getLongLE(rawCentralDirectoryData, offset));
                offset += DWORD;
            }
            if (hasCompressedSize) {
                setCompressedSize(ByteUtils.getLongLE(rawCentralDirectoryData, offset));
                offset += DWORD;
            }
            if (hasRelativeHeaderOffset) {
                setRelativeHeaderOffset(ByteUtils.getLongLE(rawCentralDirectoryData, offset));
                offset += DWORD;
            }
            if (hasDiskStart) {
                setDiskStartNumber(ByteUtils.getIntLE(rawCentralDirectoryData, offset));
                offset += WORD; // NOSONAR - assignment as documentation
            }
        }
    }

    /// Sets the unsigned 64-bit compressed size and marks it present.
    ///
    /// @param compressedSize the raw bit pattern
    public void setCompressedSize(final long compressedSize) {
        this.compressedSize = compressedSize;
        presentFields |= 2;
    }

    /// Returns whether the unsigned 64-bit compressed size is present.
    public boolean hasCompressedSize() {
        return (presentFields & 2) != 0;
    }

    /// Clears the unsigned 64-bit compressed size.
    public void clearCompressedSize() {
        presentFields &= ~2;
        compressedSize = 0;
    }

    /// Sets the unsigned 32-bit starting disk number and marks it present.
    ///
    /// @param diskStart the raw bit pattern
    public void setDiskStartNumber(final int diskStart) {
        this.diskStart = diskStart;
        presentFields |= 8;
    }

    /// Returns whether the unsigned 32-bit starting disk number is present.
    public boolean hasDiskStartNumber() {
        return (presentFields & 8) != 0;
    }

    /// Clears the unsigned 32-bit starting disk number.
    public void clearDiskStartNumber() {
        presentFields &= ~8;
        diskStart = 0;
    }

    /// Sets the unsigned 64-bit local header offset and marks it present.
    ///
    /// @param relativeHeaderOffset the raw bit pattern
    public void setRelativeHeaderOffset(final long relativeHeaderOffset) {
        this.relativeHeaderOffset = relativeHeaderOffset;
        presentFields |= 4;
    }

    /// Returns whether the unsigned 64-bit local header offset is present.
    public boolean hasRelativeHeaderOffset() {
        return (presentFields & 4) != 0;
    }

    /// Clears the unsigned 64-bit local header offset.
    public void clearRelativeHeaderOffset() {
        presentFields &= ~4;
        relativeHeaderOffset = 0;
    }

    /// Sets the unsigned 64-bit uncompressed size and marks it present.
    ///
    /// @param size the raw bit pattern
    public void setSize(final long size) {
        this.size = size;
        presentFields |= 1;
    }

    /// Returns whether the unsigned 64-bit uncompressed size is present.
    public boolean hasSize() {
        return (presentFields & 1) != 0;
    }

    /// Clears the unsigned 64-bit uncompressed size.
    public void clearSize() {
        presentFields &= ~1;
        size = 0;
    }
}
