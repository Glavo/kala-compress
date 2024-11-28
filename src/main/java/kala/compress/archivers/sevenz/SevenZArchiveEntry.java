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
package kala.compress.archivers.sevenz;

import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Objects;

import kala.compress.archivers.ArchiveEntry;
import kala.compress.utils.TimeUtils;

/**
 * An entry in a 7z archive.
 *
 * @NotThreadSafe
 * @since 1.6
 */
public class SevenZArchiveEntry implements ArchiveEntry {

    static final SevenZArchiveEntry[] EMPTY_SEVEN_Z_ARCHIVE_ENTRY_ARRAY = {};

    private String name;
    private boolean hasStream;
    private boolean isDirectory;
    private boolean isAntiItem;
    private boolean hasCreationTime;
    private boolean hasLastModifiedTime;
    private boolean hasAccessTime;
    private FileTime creationTime;
    private FileTime lastModifiedTime;
    private FileTime accessTime;
    private boolean hasWindowsAttributes;
    private int windowsAttributes;
    private boolean hasCrc;
    private long crc, compressedCrc;
    private long size, compressedSize;
    private Iterable<? extends SevenZMethodConfiguration> contentMethods;

    /**
     * Constructs a new instance.
     */
    public SevenZArchiveEntry() {
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final SevenZArchiveEntry other = (SevenZArchiveEntry) obj;
        return Objects.equals(name, other.name) && hasStream == other.hasStream && isDirectory == other.isDirectory && isAntiItem == other.isAntiItem
               && hasCreationTime == other.hasCreationTime && hasLastModifiedTime == other.hasLastModifiedTime && hasAccessTime == other.hasAccessTime
               && Objects.equals(creationTime, other.creationTime) && Objects.equals(lastModifiedTime, other.lastModifiedTime)
               && Objects.equals(accessTime, other.accessTime) && hasWindowsAttributes == other.hasWindowsAttributes
               && windowsAttributes == other.windowsAttributes && hasCrc == other.hasCrc && crc == other.crc && compressedCrc == other.compressedCrc
               && size == other.size && compressedSize == other.compressedSize && equalSevenZMethods(contentMethods, other.contentMethods);
    }

    private boolean equalSevenZMethods(final Iterable<? extends SevenZMethodConfiguration> c1, final Iterable<? extends SevenZMethodConfiguration> c2) {
        if (c1 == null) {
            return c2 == null;
        }
        if (c2 == null) {
            return false;
        }
        final Iterator<? extends SevenZMethodConfiguration> i2 = c2.iterator();
        for (final SevenZMethodConfiguration element : c1) {
            if (!i2.hasNext()) {
                return false;
            }
            if (!element.equals(i2.next())) {
                return false;
            }
        }
        return !i2.hasNext();
    }

    /**
     * Gets the access time.
     *
     * @throws UnsupportedOperationException if the entry hasn't got an access time.
     * @return the access time
     * @since 1.23
     */
    public FileTime getAccessTime() {
        if (hasAccessTime) {
            return accessTime;
        }
        throw new UnsupportedOperationException("The entry doesn't have this timestamp");
    }

    /**
     * Gets the compressed CRC.
     *
     * @return the CRC
     * @apiNote This method has a different signature in commons-compress.
     * @since 1.27.1-0
     */
    long getCompressedCrc() {
        return compressedCrc;
    }

    /**
     * Gets this entry's compressed file size.
     *
     * @return This entry's compressed file size.
     */
    long getCompressedSize() {
        return compressedSize;
    }

    /**
     * Gets the (compression) methods to use for entry's content - the default is LZMA2.
     *
     * <p>
     * Currently only {@link SevenZMethod#COPY}, {@link SevenZMethod#LZMA2}, {@link SevenZMethod#BZIP2} and {@link SevenZMethod#DEFLATE} are supported when
     * writing archives.
     * </p>
     *
     * <p>
     * The methods will be consulted in iteration order to create the final output.
     * </p>
     *
     * @since 1.8
     * @return the methods to use for the content
     */
    public Iterable<? extends SevenZMethodConfiguration> getContentMethods() {
        return contentMethods;
    }

    /**
     * Gets the CRC.
     *
     * @return the CRC
     * @apiNote This method has a different signature in commons-compress.
     * @since 1.27.1-0
     */
    public long getCrc() {
        return crc;
    }

    /**
     * Gets the CRC.
     *
     * @since 1.7
     * @return the CRC
     * @deprecated Use {@link #getCrc()}
     */
    @Deprecated
    public long getCrcValue() {
        return getCrc();
    }

    /**
     * Gets the creation time.
     *
     * @throws UnsupportedOperationException if the entry hasn't got a creation time.
     * @return the creation time
     * @since 1.23
     */
    public FileTime getCreationTime() {
        if (hasCreationTime) {
            return creationTime;
        }
        throw new UnsupportedOperationException("The entry doesn't have this timestamp");
    }

    /**
     * Gets whether this entry has got an access time at all.
     *
     * @return whether this entry has got an access time at all.
     */
    public boolean getHasAccessTime() {
        return hasAccessTime;
    }

    /**
     * Gets whether this entry has got a crc.
     *
     * <p>
     * In general entries without streams don't have a CRC either.
     * </p>
     *
     * @return whether this entry has got a crc.
     */
    public boolean getHasCrc() {
        return hasCrc;
    }

    /**
     * Gets whether this entry has got a creation time at all.
     *
     * @return whether the entry has got a creation time
     */
    public boolean getHasCreationTime() {
        return hasCreationTime;
    }

    /**
     * Gets whether this entry has got a last modified time at all.
     *
     * @return whether this entry has got a last modified time at all
     */
    public boolean getHasLastModifiedTime() {
        return hasLastModifiedTime;
    }

    /**
     * Gets whether this entry has windows attributes.
     *
     * @return whether this entry has windows attributes.
     */
    public boolean getHasWindowsAttributes() {
        return hasWindowsAttributes;
    }

    /**
     * Gets the last modified time.
     *
     * @throws UnsupportedOperationException if the entry hasn't got a last modified time.
     * @return the last modified time
     * @since 1.23
     */
    @Override
    public FileTime getLastModifiedTime() {
        if (hasLastModifiedTime) {
            return lastModifiedTime;
        }
        throw new UnsupportedOperationException("The entry doesn't have this timestamp");
    }

    /**
     * Gets this entry's name.
     *
     * <p>
     * This method returns the raw name as it is stored inside of the archive.
     * </p>
     *
     * @return This entry's name.
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Gets this entry's file size.
     *
     * @return This entry's file size.
     */
    @Override
    public long getSize() {
        return size;
    }

    /**
     * Gets the windows attributes.
     *
     * @return the windows attributes
     */
    public int getWindowsAttributes() {
        return windowsAttributes;
    }

    @Override
    public int hashCode() {
        final String n = getName();
        return n == null ? 0 : n.hashCode();
    }

    /**
     * Tests whether there is any content associated with this entry.
     *
     * @return whether there is any content associated with this entry.
     */
    public boolean hasStream() {
        return hasStream;
    }

    /**
     * Tests whether this is an "anti-item" used in differential backups, meaning it should delete the same file from a previous backup.
     *
     * @return true if it is an anti-item, false otherwise
     */
    public boolean isAntiItem() {
        return isAntiItem;
    }

    /**
     * Tests whether or not this entry represents a directory.
     *
     * @return True if this entry is a directory.
     */
    @Override
    public boolean isDirectory() {
        return isDirectory;
    }

    /**
     * Sets the access time using NTFS time (100 nanosecond units since 1 January 1601)
     *
     * @param ntfsAccessTime the access time
     */
    public void setAccessTime(final long ntfsAccessTime) {
        this.accessTime = TimeUtils.ntfsTimeToFileTime(ntfsAccessTime);
    }

    /**
     * Sets the access time.
     *
     * @param time the new access time
     * @since 1.23
     */
    public void setAccessTime(final FileTime time) {
        hasAccessTime = time != null;
        if (hasAccessTime) {
            this.accessTime = time;
        }
    }

    /**
     * Sets whether this is an "anti-item" used in differential backups, meaning it should delete the same file from a previous backup.
     *
     * @param isAntiItem true if it is an anti-item, false otherwise
     */
    public void setAntiItem(final boolean isAntiItem) {
        this.isAntiItem = isAntiItem;
    }

    /**
     * Sets the compressed CRC.
     *
     * @param crc the CRC
     */
    void setCompressedCrc(final long crc) {
        this.compressedCrc = crc;
    }

    /**
     * Sets this entry's compressed file size.
     *
     * @param size This entry's new compressed file size.
     */
    void setCompressedSize(final long size) {
        this.compressedSize = size;
    }

    /**
     * Sets the (compression) methods to use for entry's content - the default is LZMA2.
     *
     * <p>
     * Currently only {@link SevenZMethod#COPY}, {@link SevenZMethod#LZMA2}, {@link SevenZMethod#BZIP2} and {@link SevenZMethod#DEFLATE} are supported when
     * writing archives.
     * </p>
     *
     * <p>
     * The methods will be consulted in iteration order to create the final output.
     * </p>
     *
     * @param methods the methods to use for the content
     * @since 1.8
     */
    public void setContentMethods(final Iterable<? extends SevenZMethodConfiguration> methods) {
        if (methods != null) {
            final LinkedList<SevenZMethodConfiguration> l = new LinkedList<>();
            methods.forEach(l::addLast);
            contentMethods = Collections.unmodifiableList(l);
        } else {
            contentMethods = null;
        }
    }

    /**
     * Sets the (compression) methods to use for entry's content - the default is LZMA2.
     *
     * <p>
     * Currently only {@link SevenZMethod#COPY}, {@link SevenZMethod#LZMA2}, {@link SevenZMethod#BZIP2} and {@link SevenZMethod#DEFLATE} are supported when
     * writing archives.
     * </p>
     *
     * <p>
     * The methods will be consulted in iteration order to create the final output.
     * </p>
     *
     * @param methods the methods to use for the content
     * @since 1.22
     */
    public void setContentMethods(final SevenZMethodConfiguration... methods) {
        setContentMethods(Arrays.asList(methods));
    }

    /**
     * Sets the CRC.
     *
     * @param crc the CRC
     * @apiNote This method has a different signature in commons-compress.
     * @since 1.27.1-0
     */
    public void setCrc(final long crc) {
        this.crc = crc;
    }

    /**
     * Sets the CRC.
     *
     * @since 1.7
     * @param crc the CRC
     * @deprecated Use {@link #setCrc(long)}
     */
    @Deprecated
    public void setCrcValue(final long crc) {
        setCrc(crc);
    }

    /**
     * Sets the creation time using NTFS time (100 nanosecond units since 1 January 1601)
     *
     * @param ntfsCreationTime the creation time
     * @since 1.27.1-0
     */
    public void setCreationTime(final long ntfsCreationTime) {
        this.creationTime = TimeUtils.ntfsTimeToFileTime(ntfsCreationTime);
    }

    /**
     * Sets the creation time.
     *
     * @param time the new creation time
     * @since 1.23
     */
    public void setCreationTime(final FileTime time) {
        hasCreationTime = time != null;
        if (hasCreationTime) {
            this.creationTime = time;
        }
    }

    /**
     * Sets whether or not this entry represents a directory.
     *
     * @param isDirectory True if this entry is a directory.
     */
    public void setDirectory(final boolean isDirectory) {
        this.isDirectory = isDirectory;
    }

    /**
     * Sets whether this entry has got an access time at all.
     *
     * @param hasAcessTime whether this entry has got an access time at all.
     * @since 1.27.1-0
     */
    public void setHasAccessTime(final boolean hasAcessTime) {
        this.hasAccessTime = hasAcessTime;
    }

    /**
     * Sets whether this entry has got a crc.
     *
     * @param hasCrc whether this entry has got a crc.
     */
    public void setHasCrc(final boolean hasCrc) {
        this.hasCrc = hasCrc;
    }

    /**
     * Sets whether this entry has got a creation time at all.
     *
     * @param hasCreationTime whether the entry has got a creation time
     * @since 1.27.1-0
     */
    public void setHasCreationTime(final boolean hasCreationTime) {
        this.hasCreationTime = hasCreationTime;
    }

    /**
     * Sets whether this entry has got a last modified time at all.
     *
     * @param hasLastModifiedTime whether this entry has got a last modified time at all
     * @since 1.27.1-0
     */
    public void setHasLastModifiedTime(final boolean hasLastModifiedTime) {
        this.hasLastModifiedTime = hasLastModifiedTime;
    }

    /**
     * Sets whether there is any content associated with this entry.
     *
     * @param hasStream whether there is any content associated with this entry.
     */
    public void setHasStream(final boolean hasStream) {
        this.hasStream = hasStream;
    }

    /**
     * Sets whether this entry has windows attributes.
     *
     * @param hasWindowsAttributes whether this entry has windows attributes.
     */
    public void setHasWindowsAttributes(final boolean hasWindowsAttributes) {
        this.hasWindowsAttributes = hasWindowsAttributes;
    }

    /**
     * Sets the last modified time using NTFS time (100 nanosecond units since 1 January 1601)
     *
     * @param ntfsLastModifiedTime the last modified time
     */
    public void setLastModifiedTime(final long ntfsLastModifiedTime) {
        this.lastModifiedTime = TimeUtils.ntfsTimeToFileTime(ntfsLastModifiedTime);
    }

    /**
     * Sets the last modified time.
     *
     * @param time the new last modified time
     * @since 1.23
     */
    public void setLastModifiedTime(final FileTime time) {
        hasLastModifiedTime = time != null;
        if (hasLastModifiedTime) {
            this.lastModifiedTime = time;
        }
    }

    /**
     * Sets this entry's name.
     *
     * @param name This entry's new name.
     */
    public void setName(final String name) {
        this.name = name;
    }

    /**
     * Sets this entry's file size.
     *
     * @param size This entry's new file size.
     */
    public void setSize(final long size) {
        this.size = size;
    }

    /**
     * Sets the windows attributes.
     *
     * @param windowsAttributes the windows attributes
     */
    public void setWindowsAttributes(final int windowsAttributes) {
        this.windowsAttributes = windowsAttributes;
    }
}
