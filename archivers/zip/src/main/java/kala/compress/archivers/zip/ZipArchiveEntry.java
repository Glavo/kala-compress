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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

import kala.compress.archivers.ArchiveEntry;
import kala.compress.archivers.EntryStreamOffsets;
import kala.compress.utils.ByteUtils;
import kala.compress.utils.TimeUtils;

/// A ZIP entry with structured extra fields and internal and external file attributes.
///
/// Extra data follows [APPNOTE.TXT](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT):
/// each field starts with a two-byte identifier and a two-byte data length.
/// Data that does not follow this structure is retained as unparseable extra data.
/// Parsed timestamp fields update the entry times, with NTFS values taking precedence
/// over extended timestamp values.
/// Time setters leave the entry unchanged if updating the timestamp extra fields fails.
///
/// This class is not thread-safe.
public class ZipArchiveEntry implements ArchiveEntry, EntryStreamOffsets, Cloneable {

    /**
     * Indicates how the comment of this entry has been determined.
     *
     * @since 1.16
     */
    public enum CommentSource {
        /**
         * The comment has been read from the archive using the encoding of the archive specified when creating the {@link ZipArchiveInputStream} or
         * {@link ZipArchiveReader} (defaults to the UTF-8).
         */
        COMMENT,
        /**
         * The comment has been read from an {@link UnicodeCommentExtraField Unicode Extra Field}.
         */
        UNICODE_EXTRA_FIELD
    }

    /**
     * How to try to parse the extra fields.
     *
     * <p>
     * Configures the behavior for:
     * </p>
     * <ul>
     * <li>What shall happen if the extra field content doesn't follow the recommended pattern of two-byte id followed by a two-byte length?</li>
     * <li>What shall happen if an extra field is generally supported by Commons Compress but its content cannot be parsed correctly? This may for example
     * happen if the archive is corrupt, it triggers a bug in Commons Compress or the extra field uses a version not (yet) supported by Commons Compress.</li>
     * </ul>
     *
     * @since 1.19
     */
    public enum ExtraFieldParsingMode implements ExtraFieldParsingBehavior {
        /**
         * Try to parse as many extra fields as possible and wrap unknown extra fields as well as supported extra fields that cannot be parsed in
         * {@link UnrecognizedExtraField}.
         *
         * <p>
         * Wrap extra data that doesn't follow the recommended pattern in an {@link UnparseableExtraFieldData} instance.
         * </p>
         *
         * <p>
         * This is the default behavior starting with Commons Compress 1.19.
         * </p>
         */
        BEST_EFFORT(ExtraFieldUtils.UnparseableExtraField.READ) {
            @Override
            public ZipExtraField fill(final ZipExtraField field, final byte[] data, final int off, final int len, final boolean local) {
                return fillAndMakeUnrecognizedOnError(field, data, off, len, local);
            }
        },
        /**
         * Try to parse as many extra fields as possible and wrap unknown extra fields in {@link UnrecognizedExtraField}.
         *
         * <p>
         * Wrap extra data that doesn't follow the recommended pattern in an {@link UnparseableExtraFieldData} instance.
         * </p>
         *
         * <p>
         * Throw an exception if an extra field that is generally supported cannot be parsed.
         * </p>
         *
         * <p>
         * This used to be the default behavior prior to Commons Compress 1.19.
         * </p>
         */
        STRICT_FOR_KNOW_EXTRA_FIELDS(ExtraFieldUtils.UnparseableExtraField.READ),
        /**
         * Try to parse as many extra fields as possible and wrap unknown extra fields as well as supported extra fields that cannot be parsed in
         * {@link UnrecognizedExtraField}.
         *
         * <p>
         * Ignore extra data that doesn't follow the recommended pattern.
         * </p>
         */
        ONLY_PARSEABLE_LENIENT(ExtraFieldUtils.UnparseableExtraField.SKIP) {
            @Override
            public ZipExtraField fill(final ZipExtraField field, final byte[] data, final int off, final int len, final boolean local) {
                return fillAndMakeUnrecognizedOnError(field, data, off, len, local);
            }
        },
        /**
         * Try to parse as many extra fields as possible and wrap unknown extra fields in {@link UnrecognizedExtraField}.
         *
         * <p>
         * Ignore extra data that doesn't follow the recommended pattern.
         * </p>
         *
         * <p>
         * Throw an exception if an extra field that is generally supported cannot be parsed.
         * </p>
         */
        ONLY_PARSEABLE_STRICT(ExtraFieldUtils.UnparseableExtraField.SKIP),
        /**
         * Throw an exception if any of the recognized extra fields cannot be parsed or any extra field violates the recommended pattern.
         */
        DRACONIC(ExtraFieldUtils.UnparseableExtraField.THROW);

        private static ZipExtraField fillAndMakeUnrecognizedOnError(final ZipExtraField field, final byte[] data, final int off, final int len,
                                                                    final boolean local) {
            try {
                return ExtraFieldUtils.fillExtraField(field, data, off, len, local);
            } catch (final ZipException ex) {
                final UnrecognizedExtraField u = new UnrecognizedExtraField();
                u.setHeaderId(field.getHeaderId());
                if (local) {
                    u.setLocalFileDataData(Arrays.copyOfRange(data, off, off + len));
                } else {
                    u.setCentralDirectoryData(Arrays.copyOfRange(data, off, off + len));
                }
                return u;
            }
        }

        private final ExtraFieldUtils.UnparseableExtraField onUnparseableData;

        ExtraFieldParsingMode(final ExtraFieldUtils.UnparseableExtraField onUnparseableData) {
            this.onUnparseableData = onUnparseableData;
        }

        @Override
        public ZipExtraField createExtraField(final ZipShort headerId) {
            return ExtraFieldUtils.createExtraField(headerId);
        }

        @Override
        public ZipExtraField fill(final ZipExtraField field, final byte[] data, final int off, final int len, final boolean local) throws ZipException {
            return ExtraFieldUtils.fillExtraField(field, data, off, len, local);
        }

        @Override
        public ZipExtraField onUnparseableExtraField(final byte[] data, final int off, final int len, final boolean local, final int claimedLength)
                throws ZipException {
            return onUnparseableData.onUnparseableExtraField(data, off, len, local, claimedLength);
        }
    }

    /**
     * Indicates how the name of this entry has been determined.
     *
     * @since 1.16
     */
    public enum NameSource {
        /**
         * The name has been read from the archive using the encoding of the archive specified when creating the {@link ZipArchiveInputStream} or
         * {@link ZipArchiveReader} (defaults to the platform's default encoding).
         */
        NAME,
        /**
         * The name has been read from the archive and the archive specified the EFS flag which indicates the name has been encoded as UTF-8.
         */
        NAME_WITH_EFS_FLAG,
        /**
         * The name has been read from an {@link UnicodePathExtraField Unicode Extra Field}.
         */
        UNICODE_EXTRA_FIELD
    }

    private static final String ZIP_DIR_SEP = "/";

    static final ZipArchiveEntry[] EMPTY_ARRAY = {};

    public static final int PLATFORM_UNIX = 3;
    public static final int PLATFORM_FAT = 0;
    public static final int CRC_UNKNOWN = -1;

    /// The compression method for uncompressed entries.
    public static final int STORED = 0;

    /// The compression method for entries compressed with DEFLATE.
    public static final int DEFLATED = 8;

    /// The ZIP format constant defined by [ZipEntry#LOCSIG].
    public static final long LOCSIG = ZipEntry.LOCSIG;

    /// The ZIP format constant defined by [ZipEntry#EXTSIG].
    public static final long EXTSIG = ZipEntry.EXTSIG;

    /// The ZIP format constant defined by [ZipEntry#CENSIG].
    public static final long CENSIG = ZipEntry.CENSIG;

    /// The ZIP format constant defined by [ZipEntry#ENDSIG].
    public static final long ENDSIG = ZipEntry.ENDSIG;

    /// The ZIP format constant defined by [ZipEntry#LOCHDR].
    public static final int LOCHDR = ZipEntry.LOCHDR;

    /// The ZIP format constant defined by [ZipEntry#EXTHDR].
    public static final int EXTHDR = ZipEntry.EXTHDR;

    /// The ZIP format constant defined by [ZipEntry#CENHDR].
    public static final int CENHDR = ZipEntry.CENHDR;

    /// The ZIP format constant defined by [ZipEntry#ENDHDR].
    public static final int ENDHDR = ZipEntry.ENDHDR;

    /// The ZIP format constant defined by [ZipEntry#LOCVER].
    public static final int LOCVER = ZipEntry.LOCVER;

    /// The ZIP format constant defined by [ZipEntry#LOCFLG].
    public static final int LOCFLG = ZipEntry.LOCFLG;

    /// The ZIP format constant defined by [ZipEntry#LOCHOW].
    public static final int LOCHOW = ZipEntry.LOCHOW;

    /// The ZIP format constant defined by [ZipEntry#LOCTIM].
    public static final int LOCTIM = ZipEntry.LOCTIM;

    /// The ZIP format constant defined by [ZipEntry#LOCCRC].
    public static final int LOCCRC = ZipEntry.LOCCRC;

    /// The ZIP format constant defined by [ZipEntry#LOCSIZ].
    public static final int LOCSIZ = ZipEntry.LOCSIZ;

    /// The ZIP format constant defined by [ZipEntry#LOCLEN].
    public static final int LOCLEN = ZipEntry.LOCLEN;

    /// The ZIP format constant defined by [ZipEntry#LOCNAM].
    public static final int LOCNAM = ZipEntry.LOCNAM;

    /// The ZIP format constant defined by [ZipEntry#LOCEXT].
    public static final int LOCEXT = ZipEntry.LOCEXT;

    /// The ZIP format constant defined by [ZipEntry#EXTCRC].
    public static final int EXTCRC = ZipEntry.EXTCRC;

    /// The ZIP format constant defined by [ZipEntry#EXTSIZ].
    public static final int EXTSIZ = ZipEntry.EXTSIZ;

    /// The ZIP format constant defined by [ZipEntry#EXTLEN].
    public static final int EXTLEN = ZipEntry.EXTLEN;

    /// The ZIP format constant defined by [ZipEntry#CENVEM].
    public static final int CENVEM = ZipEntry.CENVEM;

    /// The ZIP format constant defined by [ZipEntry#CENVER].
    public static final int CENVER = ZipEntry.CENVER;

    /// The ZIP format constant defined by [ZipEntry#CENFLG].
    public static final int CENFLG = ZipEntry.CENFLG;

    /// The ZIP format constant defined by [ZipEntry#CENHOW].
    public static final int CENHOW = ZipEntry.CENHOW;

    /// The ZIP format constant defined by [ZipEntry#CENTIM].
    public static final int CENTIM = ZipEntry.CENTIM;

    /// The ZIP format constant defined by [ZipEntry#CENCRC].
    public static final int CENCRC = ZipEntry.CENCRC;

    /// The ZIP format constant defined by [ZipEntry#CENSIZ].
    public static final int CENSIZ = ZipEntry.CENSIZ;

    /// The ZIP format constant defined by [ZipEntry#CENLEN].
    public static final int CENLEN = ZipEntry.CENLEN;

    /// The ZIP format constant defined by [ZipEntry#CENNAM].
    public static final int CENNAM = ZipEntry.CENNAM;

    /// The ZIP format constant defined by [ZipEntry#CENEXT].
    public static final int CENEXT = ZipEntry.CENEXT;

    /// The ZIP format constant defined by [ZipEntry#CENCOM].
    public static final int CENCOM = ZipEntry.CENCOM;

    /// The ZIP format constant defined by [ZipEntry#CENDSK].
    public static final int CENDSK = ZipEntry.CENDSK;

    /// The ZIP format constant defined by [ZipEntry#CENATT].
    public static final int CENATT = ZipEntry.CENATT;

    /// The ZIP format constant defined by [ZipEntry#CENATX].
    public static final int CENATX = ZipEntry.CENATX;

    /// The ZIP format constant defined by [ZipEntry#CENOFF].
    public static final int CENOFF = ZipEntry.CENOFF;

    /// The ZIP format constant defined by [ZipEntry#ENDSUB].
    public static final int ENDSUB = ZipEntry.ENDSUB;

    /// The ZIP format constant defined by [ZipEntry#ENDTOT].
    public static final int ENDTOT = ZipEntry.ENDTOT;

    /// The ZIP format constant defined by [ZipEntry#ENDSIZ].
    public static final int ENDSIZ = ZipEntry.ENDSIZ;

    /// The ZIP format constant defined by [ZipEntry#ENDOFF].
    public static final int ENDOFF = ZipEntry.ENDOFF;

    /// The ZIP format constant defined by [ZipEntry#ENDCOM].
    public static final int ENDCOM = ZipEntry.ENDCOM;

    private static final int SHORT_MASK = 0xFFFF;

    private static final int SHORT_SHIFT = 16;

    /// The DOS timestamp used for dates before 1980.
    private static final long DOSTIME_BEFORE_1980 = 0x00210000L;

    private static boolean canConvertToInfoZipExtendedTimestamp(final FileTime lastModifiedTime, final FileTime lastAccessTime, final FileTime creationTime) {
        return TimeUtils.isUnixTime(lastModifiedTime) && TimeUtils.isUnixTime(lastAccessTime) && TimeUtils.isUnixTime(creationTime);
    }

    /// Restores extra-field precision when the JDK timestamp is absent or matches its truncated microsecond value.
    ///
    /// @param extraTime the parsed timestamp, or null
    /// @param entryTime the JDK timestamp, or null
    /// @return the restored timestamp, or the JDK timestamp if it differs
    private static FileTime restoreTimestampPrecision(final FileTime extraTime, final FileTime entryTime) {
        if (extraTime != null && (entryTime == null
                || FileTime.from(extraTime.toInstant().truncatedTo(ChronoUnit.MICROS)).equals(entryTime))) {
            return extraTime;
        }
        return entryTime;
    }

    private static boolean isDirectoryEntryName(final String entryName) {
        return entryName.endsWith(ZIP_DIR_SEP);
    }

    private static String toDirectoryEntryName(final String entryName) {
        return isDirectoryEntryName(entryName) ? entryName : entryName + ZIP_DIR_SEP;
    }

    private static String toEntryName(final Path inputPath, final String entryName, final LinkOption... options) {
        return Files.isDirectory(inputPath, options) ? toDirectoryEntryName(entryName) : entryName;
    }

    /// The compression method, or -1 if unspecified.
    private int method = ZipMethod.UNKNOWN_CODE;

    /// The uncompressed size in bytes, or -1 if unknown.
    private long size = SIZE_UNKNOWN;
    /// The compressed size in bytes, or -1 if unknown.
    private long compressedSize = SIZE_UNKNOWN;
    /// The unsigned CRC-32 value, or -1 if unknown.
    private long crc = CRC_UNKNOWN;
    /// The optional entry comment.
    private String comment;
    /// The serialized local extra fields, or null if never set.
    private byte[] extra;
    /// The DOS timestamp in the low 32 bits and its millisecond remainder in the high 32 bits, or -1 if unset.
    private long xdostime = -1;
    /// The modification time requiring timestamp extra fields, or null when only a DOS time is available.
    private FileTime lastModifiedTime;
    /// The optional last access time.
    private FileTime lastAccessTime;
    /// The optional creation time.
    private FileTime creationTime;
    private int internalAttributes;
    private int versionRequired;
    private int versionMadeBy;
    private int platform = PLATFORM_FAT;
    private int rawFlag;
    private long externalAttributes;
    private int alignment;
    private ZipExtraField[] extraFields;
    private UnparseableExtraFieldData unparseableExtra;
    private String name;
    private byte[] rawName;
    private GeneralPurposeBit generalPurposeBit = new GeneralPurposeBit();
    private long localHeaderOffset = OFFSET_UNKNOWN;
    private long dataOffset = OFFSET_UNKNOWN;
    private boolean isStreamContiguous;

    private NameSource nameSource = NameSource.NAME;

    private final Function<ZipShort, ZipExtraField> extraFieldFactory;

    private CommentSource commentSource = CommentSource.COMMENT;

    private long diskNumberStart;

    /**
     *
     */
    protected ZipArchiveEntry() {
        this("");
    }

    /**
     * Creates a new ZIP entry taking some information from the given path and using the provided name.
     *
     * <p>
     * The name will be adjusted to end with a forward slash "/" if the file is a directory. If the file is not a directory a potential trailing forward slash
     * will be stripped from the entry name.
     * </p>
     *
     * @param extraFieldFactory custom lookup factory for extra fields or null
     * @param inputPath         path to create the entry from.
     * @param entryName         name of the entry.
     * @param options           options indicating how symbolic links are handled.
     * @throws IOException if an I/O error occurs.
     */
    private ZipArchiveEntry(final Function<ZipShort, ZipExtraField> extraFieldFactory, final Path inputPath, final String entryName,
                            final LinkOption... options) throws IOException {
        this(extraFieldFactory, toEntryName(inputPath, entryName, options));
        setAttributes(inputPath, options);
    }

    /**
     * Creates a new ZIP entry with the specified name.
     *
     * <p>
     * Assumes the entry represents a directory if and only if the name ends with a forward slash "/".
     * </p>
     *
     * @param extraFieldFactory custom lookup factory for extra fields or null
     * @param name              the name of the entry
     */
    private ZipArchiveEntry(final Function<ZipShort, ZipExtraField> extraFieldFactory, final String name) {
        this.extraFieldFactory = extraFieldFactory;
        setName(name);
    }

    /**
     * Creates a new ZIP entry with fields taken from the specified ZIP entry.
     *
     * <p>
     * Assumes the entry represents a directory if and only if the name ends with a forward slash "/".
     * </p>
     *
     * @param extraFieldFactory the extra field lookup factory.
     * @param entry             the entry to get fields from
     * @throws ZipException on error
     */
    private ZipArchiveEntry(final Function<ZipShort, ZipExtraField> extraFieldFactory, final ZipEntry entry) throws ZipException {
        this(extraFieldFactory, entry.getName());
        method = entry.getMethod();
        size = entry.getSize();
        compressedSize = entry.getCompressedSize();
        crc = entry.getCrc();
        comment = entry.getComment();
        final FileTime modified = entry.getLastModifiedTime();
        LocalDateTime localTime = null;
        if (modified != null) {
            try {
                localTime = entry.getTimeLocal();
            } catch (final DateTimeException e) {
                // JDK entries may contain DOS dates with invalid calendar fields.
                localTime = LocalDateTime.ofInstant(modified.toInstant(), ZoneId.systemDefault());
            }
        }
        final byte[] extra = entry.getExtra();
        if (extra != null) {
            setExtraFields(parseExtraFields(extra, true, ExtraFieldParsingMode.BEST_EFFORT));
        }
        if (localTime != null) {
            xdostime = toExtendedDosTime(localTime);
            if (lastModifiedTime != null || localTime.getYear() < 1980 || localTime.getYear() > 2099
                    || localTime.getNano() % 1_000_000 != 0) {
                lastModifiedTime = restoreTimestampPrecision(lastModifiedTime, modified);
            }
        }
        lastAccessTime = restoreTimestampPrecision(lastAccessTime, entry.getLastAccessTime());
        creationTime = restoreTimestampPrecision(creationTime, entry.getCreationTime());
        setExtraTimeFields();
    }

    /**
     * Creates a new ZIP entry taking some information from the given path and using the provided name.
     *
     * <p>
     * The name will be adjusted to end with a forward slash "/" if the file is a directory. If the file is not a directory a potential trailing forward slash
     * will be stripped from the entry name.
     * </p>
     *
     * @param inputPath path to create the entry from.
     * @param entryName name of the entry.
     * @param options   options indicating how symbolic links are handled.
     * @throws IOException if an I/O error occurs.
     * @since 1.21
     */
    public ZipArchiveEntry(final Path inputPath, final String entryName, final LinkOption... options) throws IOException {
        this(null, inputPath, entryName, options);
    }

    /// Creates a ZIP entry with the specified name.
    /// The entry is a directory if its normalized name ends with `/`.
    ///
    /// @param name the entry name, not null; backslashes are replaced with `/` if no `/` is present
    /// @throws NullPointerException if name is null
    /// @throws IllegalArgumentException if name exceeds 65535 characters
    /// @since 1.26.0
    public ZipArchiveEntry(final String name) {
        this((Function<ZipShort, ZipExtraField>) null, name);
    }

    /// Creates a ZIP entry by copying the entry metadata.
    /// Extra field objects are shared with the original entry.
    ///
    /// @param entry the entry to copy, not null
    /// @throws NullPointerException if entry is null
    public ZipArchiveEntry(final ZipArchiveEntry entry) {
        extraFieldFactory = entry.extraFieldFactory;
        name = entry.name;
        rawName = entry.getRawName();
        method = entry.method;
        size = entry.size;
        compressedSize = entry.compressedSize;
        crc = entry.crc;
        comment = entry.comment;
        xdostime = entry.xdostime;
        lastModifiedTime = entry.lastModifiedTime;
        lastAccessTime = entry.lastAccessTime;
        creationTime = entry.creationTime;
        internalAttributes = entry.internalAttributes;
        externalAttributes = entry.externalAttributes;
        versionRequired = entry.versionRequired;
        versionMadeBy = entry.versionMadeBy;
        platform = entry.platform;
        rawFlag = entry.rawFlag;
        alignment = entry.alignment;
        extraFields = entry.extraFields == null ? null : entry.extraFields.clone();
        unparseableExtra = entry.unparseableExtra;
        extra = entry.extra == null ? null : entry.extra.clone();
        generalPurposeBit = entry.generalPurposeBit == null ? null : (GeneralPurposeBit) entry.generalPurposeBit.clone();
        localHeaderOffset = entry.localHeaderOffset;
        dataOffset = entry.dataOffset;
        isStreamContiguous = entry.isStreamContiguous;
        nameSource = entry.nameSource;
        commentSource = entry.commentSource;
        diskNumberStart = entry.diskNumberStart;
    }

    /**
     * Creates a new ZIP entry with fields taken from the specified ZIP entry.
     *
     * <p>
     * Assumes the entry represents a directory if and only if the name ends with a forward slash "/".
     * </p>
     *
     * @param entry the entry to get fields from
     * @throws ZipException on error
     */
    public ZipArchiveEntry(final ZipEntry entry) throws ZipException {
        this(null, entry);
    }

    /**
     * Adds an extra field - replacing an already present extra field of the same type.
     *
     * <p>
     * The new extra field will be the first one.
     * </p>
     *
     * @param ze an extra field
     */
    public void addAsFirstExtraField(final ZipExtraField ze) {
        if (ze instanceof UnparseableExtraFieldData) {
            unparseableExtra = (UnparseableExtraFieldData) ze;
        } else {
            if (getExtraField(ze.getHeaderId()) != null) {
                internalRemoveExtraField(ze.getHeaderId());
            }
            final ZipExtraField[] copy = extraFields;
            final int newLen = extraFields != null ? extraFields.length + 1 : 1;
            extraFields = new ZipExtraField[newLen];
            extraFields[0] = ze;
            if (copy != null) {
                System.arraycopy(copy, 0, extraFields, 1, extraFields.length - 1);
            }
        }
        setExtra();
        updateTimeFieldsFromExtraField(ze);
    }

    /**
     * Adds an extra field - replacing an already present extra field of the same type.
     *
     * <p>
     * If no extra field of the same type exists, the field will be added as last field.
     * </p>
     *
     * @param ze an extra field
     */
    public void addExtraField(final ZipExtraField ze) {
        internalAddExtraField(ze);
        setExtra();
        updateTimeFieldsFromExtraField(ze);
    }

    /// Creates an extended timestamp field for the supplied times.
    private static X5455_ExtendedTimestamp createInfoZipExtendedTimestamp(final FileTime lastModifiedTime, final FileTime lastAccessTime, final FileTime creationTime) {
        final X5455_ExtendedTimestamp infoZipTimestamp = new X5455_ExtendedTimestamp();
        if (lastModifiedTime != null) {
            infoZipTimestamp.setModifyFileTime(lastModifiedTime);
        }
        if (lastAccessTime != null) {
            infoZipTimestamp.setAccessFileTime(lastAccessTime);
        }
        if (creationTime != null) {
            infoZipTimestamp.setCreateFileTime(creationTime);
        }
        return infoZipTimestamp;
    }

    /// Creates an NTFS timestamp field for the supplied times.
    private static X000A_NTFS createNTFSTimestamp(final FileTime lastModifiedTime, final FileTime lastAccessTime, final FileTime creationTime) {
        final X000A_NTFS ntfsTimestamp = new X000A_NTFS();
        if (lastModifiedTime != null) {
            ntfsTimestamp.setModifyFileTime(lastModifiedTime);
        }
        if (lastAccessTime != null) {
            ntfsTimestamp.setAccessFileTime(lastAccessTime);
        }
        if (creationTime != null) {
            ntfsTimestamp.setCreateFileTime(creationTime);
        }
        return ntfsTimestamp;
    }

    /// Returns a copy of this entry. Extra field objects are shared with the original.
    @Override
    public ZipArchiveEntry clone() {
        try {
            final ZipArchiveEntry e = (ZipArchiveEntry) super.clone();
            e.extraFields = extraFields == null ? null : extraFields.clone();
            e.extra = extra == null ? null : extra.clone();
            e.rawName = getRawName();
            e.generalPurposeBit = generalPurposeBit == null ? null : (GeneralPurposeBit) generalPurposeBit.clone();
            return e;
        } catch (final CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    private ZipExtraField[] copyOf(final ZipExtraField[] src, final int length) {
        return Arrays.copyOf(src, length);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final ZipArchiveEntry other = (ZipArchiveEntry) obj;
        final String myName = getName();
        final String otherName = other.getName();
        if (!Objects.equals(myName, otherName)) {
            return false;
        }
        String myComment = getComment();
        String otherComment = other.getComment();
        if (myComment == null) {
            myComment = "";
        }
        if (otherComment == null) {
            otherComment = "";
        }
        return Objects.equals(getLastModifiedTime(), other.getLastModifiedTime()) && Objects.equals(getLastAccessTime(), other.getLastAccessTime())
               && Objects.equals(getCreationTime(), other.getCreationTime()) && myComment.equals(otherComment)
               && getInternalAttributes() == other.getInternalAttributes() && getPlatform() == other.getPlatform()
               && getExternalAttributes() == other.getExternalAttributes() && getMethod() == other.getMethod() && getSize() == other.getSize()
               && getCrc() == other.getCrc() && getCompressedSize() == other.getCompressedSize()
               && Arrays.equals(getCentralDirectoryExtra(), other.getCentralDirectoryExtra())
               && Arrays.equals(getLocalFileDataExtra(), other.getLocalFileDataExtra()) && localHeaderOffset == other.localHeaderOffset
               && dataOffset == other.dataOffset && generalPurposeBit.equals(other.generalPurposeBit);
    }

    private ZipExtraField findMatching(final ZipShort headerId, final List<ZipExtraField> fs) {
        return fs.stream().filter(f -> headerId.equals(f.getHeaderId())).findFirst().orElse(null);
    }

    private ZipExtraField findUnparseable(final List<ZipExtraField> fs) {
        return fs.stream().filter(UnparseableExtraFieldData.class::isInstance).findFirst().orElse(null);
    }

    /**
     * Gets currently configured alignment.
     *
     * @return alignment for this entry.
     * @since 1.14
     */
    protected int getAlignment() {
        return this.alignment;
    }

    private ZipExtraField[] getAllExtraFields() {
        final ZipExtraField[] allExtraFieldsNoCopy = getAllExtraFieldsNoCopy();
        return allExtraFieldsNoCopy == extraFields ? copyOf(allExtraFieldsNoCopy, allExtraFieldsNoCopy.length) : allExtraFieldsNoCopy;
    }

    /**
     * Gets all extra fields, including unparseable ones.
     *
     * @return An array of all extra fields. Not necessarily a copy of internal data structures, hence private method
     */
    private ZipExtraField[] getAllExtraFieldsNoCopy() {
        if (extraFields == null) {
            return getUnparseableOnly();
        }
        return unparseableExtra != null ? getMergedFields() : extraFields;
    }

    /**
     * Retrieves the extra data for the central directory.
     *
     * @return the central directory extra data
     */
    public byte[] getCentralDirectoryExtra() {
        return ExtraFieldUtils.mergeCentralDirectoryData(getAllExtraFieldsNoCopy());
    }

    /// Returns the entry comment, or null if unspecified.
    public String getComment() {
        return comment;
    }

    /// Returns the compressed size in bytes, or -1 if unknown.
    public long getCompressedSize() {
        return compressedSize;
    }

    /// Returns the unsigned CRC-32 value of the uncompressed data, or -1 if unknown.
    public long getCrc() {
        return crc;
    }

    /// Returns the creation time, or null if unspecified.
    public FileTime getCreationTime() {
        return creationTime;
    }

    /// Returns the serialized local extra fields, or null if never set.
    /// The returned array is not copied; modifying it does not update the structured extra fields.
    public byte[] getExtra() {
        return extra;
    }

    /// Returns the last access time, or null if unspecified.
    public FileTime getLastAccessTime() {
        return lastAccessTime;
    }

    /// Returns the modification time, or null if unspecified.
    /// A DOS local time is interpreted in the system default time zone.
    @Override
    public FileTime getLastModifiedTime() {
        return lastModifiedTime != null ? lastModifiedTime : xdostime == -1 ? null : FileTime.fromMillis(getTime());
    }

    /// Returns the modification time in milliseconds since the epoch, or -1 if unspecified.
    /// A DOS local time is interpreted in the system default time zone.
    /// Gaps are shifted forward and overlaps use the earlier offset.
    public long getTime() {
        if (lastModifiedTime != null) {
            return lastModifiedTime.toMillis();
        }
        return xdostime == -1 ? -1 : getTimeLocal().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /// Returns the local modification time, or null if unspecified.
    /// An absolute timestamp is converted using the system default time zone.
    /// A DOS timestamp is returned without time-zone adjustment, normalizing invalid fields.
    public LocalDateTime getTimeLocal() {
        if (lastModifiedTime != null) {
            return LocalDateTime.ofInstant(lastModifiedTime.toInstant(), ZoneId.systemDefault());
        }
        final int millis = (int) (xdostime >>> 32);
        return xdostime == -1 ? null : LocalDateTime.ofEpochSecond(
                TimeUtils.dosTimeToEpochMilli(xdostime, ZoneOffset.UTC) / 1000 + millis / 1000,
                millis % 1000 * 1_000_000, ZoneOffset.UTC);
    }

    /// Returns the packed DOS modification time, or -1 if unspecified.
    long getDosTime() {
        return xdostime == -1 ? -1 : xdostime & 0xffffffffL;
    }

    /**
     * The source of the comment field value.
     *
     * @return source of the comment field value
     * @since 1.16
     */
    public CommentSource getCommentSource() {
        return commentSource;
    }

    @Override
    public long getDataOffset() {
        return dataOffset;
    }

    /**
     * The number of the split segment this entry starts at.
     *
     * @return the number of the split segment this entry starts at.
     * @since 1.20
     */
    public long getDiskNumberStart() {
        return diskNumberStart;
    }

    /**
     * Retrieves the external file attributes.
     *
     * <p>
     * <strong>Note</strong>: {@link ZipArchiveInputStream} is unable to fill this field, you must use {@link ZipArchiveReader} if you want to read entries using this
     * attribute.
     * </p>
     *
     * @return the external file attributes
     */
    public long getExternalAttributes() {
        return externalAttributes;
    }

    /**
     * Gets an extra field by its header id.
     *
     * @param type the header id
     * @return null if no such field exists.
     */
    public ZipExtraField getExtraField(final ZipShort type) {
        if (extraFields != null) {
            for (final ZipExtraField extraField : extraFields) {
                if (type.equals(extraField.getHeaderId())) {
                    return extraField;
                }
            }
        }
        return null;
    }

    /**
     * Gets all extra fields that have been parsed successfully.
     *
     * <p>
     * <strong>Note</strong>: The set of extra fields may be incomplete when {@link ZipArchiveInputStream} has been used as some extra fields use the central
     * directory to store additional information.
     * </p>
     *
     * @return an array of the extra fields
     */
    public ZipExtraField[] getExtraFields() {
        return getParseableExtraFields();
    }

    /**
     * Gets extra fields.
     *
     * @param includeUnparseable whether to also return unparseable extra fields as {@link UnparseableExtraFieldData} if such data exists.
     * @return an array of the extra fields
     * @since 1.1
     */
    public ZipExtraField[] getExtraFields(final boolean includeUnparseable) {
        return includeUnparseable ? getAllExtraFields() : getParseableExtraFields();
    }

    /**
     * Gets extra fields.
     *
     * @param parsingBehavior controls parsing of extra fields.
     * @return an array of the extra fields
     * @throws ZipException if parsing fails, cannot happen if {@code
     *                      parsingBehavior}  is {@link ExtraFieldParsingMode#BEST_EFFORT}.
     * @since 1.19
     */
    public ZipExtraField[] getExtraFields(final ExtraFieldParsingBehavior parsingBehavior) throws ZipException {
        if (parsingBehavior == ExtraFieldParsingMode.BEST_EFFORT) {
            return getExtraFields(true);
        }
        if (parsingBehavior == ExtraFieldParsingMode.ONLY_PARSEABLE_LENIENT) {
            return getExtraFields(false);
        }
        final byte[] local = getExtra();
        final List<ZipExtraField> localFields = new ArrayList<>(Arrays.asList(parseExtraFields(local, true, parsingBehavior)));
        final byte[] central = getCentralDirectoryExtra();
        final List<ZipExtraField> centralFields = new ArrayList<>(Arrays.asList(parseExtraFields(central, false, parsingBehavior)));
        final List<ZipExtraField> merged = new ArrayList<>();
        for (final ZipExtraField l : localFields) {
            ZipExtraField c;
            if (l instanceof UnparseableExtraFieldData) {
                c = findUnparseable(centralFields);
            } else {
                c = findMatching(l.getHeaderId(), centralFields);
            }
            if (c != null) {
                final byte[] cd = c.getCentralDirectoryData();
                if (cd != null && cd.length > 0) {
                    l.parseFromCentralDirectoryData(cd, 0, cd.length);
                }
                centralFields.remove(c);
            }
            merged.add(l);
        }
        merged.addAll(centralFields);
        return merged.toArray(ExtraFieldUtils.EMPTY_ZIP_EXTRA_FIELD_ARRAY);
    }

    /**
     * The "general purpose bit" field.
     *
     * @return the general purpose bit
     * @since 1.1
     */
    public GeneralPurposeBit getGeneralPurposeBit() {
        return generalPurposeBit;
    }

    /**
     * Gets the internal file attributes.
     *
     * <p>
     * <strong>Note</strong>: {@link ZipArchiveInputStream} is unable to fill this field, you must use {@link ZipArchiveReader} if you want to read entries using this
     * attribute.
     * </p>
     *
     * @return the internal file attributes
     */
    public int getInternalAttributes() {
        return internalAttributes;
    }

    /**
     * Gets the extra data for the local file data.
     *
     * @return the extra data for local file
     */
    public byte[] getLocalFileDataExtra() {
        final byte[] extra = getExtra();
        return extra != null ? extra : ByteUtils.EMPTY_BYTE_ARRAY;
    }

    /**
     * Gets the local header offset.
     *
     * @return the local header offset.
     * @since 1.24.0
     */
    public long getLocalHeaderOffset() {
        return this.localHeaderOffset;
    }

    private ZipExtraField[] getMergedFields() {
        final ZipExtraField[] zipExtraFields = copyOf(extraFields, extraFields.length + 1);
        zipExtraFields[extraFields.length] = unparseableExtra;
        return zipExtraFields;
    }

    /**
     * Gets the compression method of this entry, or -1 if the compression method has not been specified.
     *
     * @return compression method
     * @since 1.1
     */
    public int getMethod() {
        return method;
    }

    /**
     * Gets the name of the entry.
     *
     * <p>
     * This method returns the raw name as it is stored inside of the archive.
     * </p>
     *
     * @return the entry name
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * The source of the name field value.
     *
     * @return source of the name field value
     * @since 1.16
     */
    public NameSource getNameSource() {
        return nameSource;
    }

    private ZipExtraField[] getParseableExtraFields() {
        final ZipExtraField[] parseableExtraFields = getParseableExtraFieldsNoCopy();
        return parseableExtraFields == extraFields ? copyOf(parseableExtraFields, parseableExtraFields.length) : parseableExtraFields;
    }

    private ZipExtraField[] getParseableExtraFieldsNoCopy() {
        if (extraFields == null) {
            return ExtraFieldUtils.EMPTY_ZIP_EXTRA_FIELD_ARRAY;
        }
        return extraFields;
    }

    /**
     * Platform specification to put into the &quot;version made by&quot; part of the central file header.
     *
     * @return PLATFORM_FAT unless {@link #setUnixMode setUnixMode} has been called, in which case PLATFORM_UNIX will be returned.
     */
    public int getPlatform() {
        return platform;
    }

    /**
     * The content of the flags field.
     *
     * @return content of the flags field
     * @since 1.11
     */
    public int getRawFlag() {
        return rawFlag;
    }

    /**
     * Returns the raw bytes that made up the name before it has been converted using the configured or guessed encoding.
     *
     * <p>
     * This method will return null if this instance has not been read from an archive.
     * </p>
     *
     * @return the raw name bytes
     * @since 1.2
     */
    public byte[] getRawName() {
        if (rawName != null) {
            return Arrays.copyOf(rawName, rawName.length);
        }
        return null;
    }

    /**
     * Gets the uncompressed size of the entry data.
     *
     * <p>
     * <strong>Note</strong>: {@link ZipArchiveInputStream} may create entries that return {@link #SIZE_UNKNOWN SIZE_UNKNOWN} as long as the entry hasn't been
     * read completely.
     * </p>
     *
     * @return the entry size
     */
    @Override
    public long getSize() {
        return size;
    }

    /**
     * Gets the Unix permission.
     *
     * @return the unix permissions
     */
    public int getUnixMode() {
        return platform != PLATFORM_UNIX ? 0 : (int) (getExternalAttributes() >> SHORT_SHIFT & SHORT_MASK);
    }

    /**
     * Gets up extra field data that couldn't be parsed correctly.
     *
     * @return null if no such field exists.
     * @since 1.1
     */
    public UnparseableExtraFieldData getUnparseableExtraFieldData() {
        return unparseableExtra;
    }

    private ZipExtraField[] getUnparseableOnly() {
        return unparseableExtra == null ? ExtraFieldUtils.EMPTY_ZIP_EXTRA_FIELD_ARRAY : new ZipExtraField[]{unparseableExtra};
    }

    /**
     * Gets the "version made by" field.
     *
     * @return "version made by" field
     * @since 1.11
     */
    public int getVersionMadeBy() {
        return versionMadeBy;
    }

    /**
     * Gets the "version required to expand" field.
     *
     * @return "version required to expand" field
     * @since 1.11
     */
    public int getVersionRequired() {
        return versionRequired;
    }

    /**
     * Gets the hash code of the entry. This uses the name as the hash code.
     *
     * @return a hash code.
     */
    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    private void internalAddExtraField(final ZipExtraField ze) {
        if (ze instanceof UnparseableExtraFieldData) {
            unparseableExtra = (UnparseableExtraFieldData) ze;
        } else if (extraFields == null) {
            extraFields = new ZipExtraField[]{ze};
        } else {
            if (getExtraField(ze.getHeaderId()) != null) {
                internalRemoveExtraField(ze.getHeaderId());
            }
            final ZipExtraField[] zipExtraFields = copyOf(extraFields, extraFields.length + 1);
            zipExtraFields[zipExtraFields.length - 1] = ze;
            extraFields = zipExtraFields;
        }
    }

    private void internalRemoveExtraField(final ZipShort type) {
        if (extraFields == null) {
            return;
        }
        final List<ZipExtraField> newResult = new ArrayList<>();
        for (final ZipExtraField extraField : extraFields) {
            if (!type.equals(extraField.getHeaderId())) {
                newResult.add(extraField);
            }
        }
        if (extraFields.length == newResult.size()) {
            return;
        }
        extraFields = newResult.toArray(ExtraFieldUtils.EMPTY_ZIP_EXTRA_FIELD_ARRAY);
    }

    /// Sets the modification time and marks it for inclusion in timestamp extra fields.
    private void internalSetLastModifiedTime(final FileTime time) {
        lastModifiedTime = Objects.requireNonNull(time, "time");
        xdostime = toExtendedDosTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(time.toMillis()), ZoneId.systemDefault()));
    }

    /**
     * Is this entry a directory?
     *
     * @return true if the entry is a directory
     */
    @Override
    public boolean isDirectory() {
        return isDirectoryEntryName(getName());
    }

    @Override
    public boolean isStreamContiguous() {
        return isStreamContiguous;
    }

    /**
     * Returns true if this entry represents a Unix symlink, in which case the entry's content contains the target path for the symlink.
     *
     * @return true if the entry represents a Unix symlink, false otherwise.
     * @since 1.5
     */
    public boolean isUnixSymlink() {
        return (getUnixMode() & UnixStat.FILE_TYPE_FLAG) == UnixStat.LINK_FLAG;
    }

    /**
     * If there are no extra fields, use the given fields as new extra data - otherwise merge the fields assuming the existing fields and the new fields stem
     * from different locations inside the archive.
     *
     * @param f     the extra fields to merge
     * @param local whether the new fields originate from local data
     */
    private void mergeExtraFields(final ZipExtraField[] f, final boolean local) {
        if (extraFields == null) {
            setExtraFields(f);
        } else {
            boolean updateTimes = false;
            for (final ZipExtraField element : f) {
                updateTimes |= isTimestampExtraField(element);
                final ZipExtraField existing;
                if (element instanceof UnparseableExtraFieldData) {
                    existing = unparseableExtra;
                } else {
                    existing = getExtraField(element.getHeaderId());
                }
                if (existing == null) {
                    internalAddExtraField(element);
                } else {
                    final byte[] b = local ? element.getLocalFileDataData() : element.getCentralDirectoryData();
                    try {
                        if (local) {
                            existing.parseFromLocalFileData(b, 0, b.length);
                        } else {
                            existing.parseFromCentralDirectoryData(b, 0, b.length);
                        }
                    } catch (final ZipException ex) {
                        // emulate ExtraFieldParsingMode.fillAndMakeUnrecognizedOnError
                        final UnrecognizedExtraField u = new UnrecognizedExtraField();
                        u.setHeaderId(existing.getHeaderId());
                        if (local) {
                            u.setLocalFileDataData(b);
                            u.setCentralDirectoryData(existing.getCentralDirectoryData());
                        } else {
                            u.setLocalFileDataData(existing.getLocalFileDataData());
                            u.setCentralDirectoryData(b);
                        }
                        internalRemoveExtraField(existing.getHeaderId());
                        internalAddExtraField(u);
                    }
                }
            }
            setExtra();
            if (updateTimes) {
                updateTimeFieldsFromExtraFields();
            }
        }
    }

    private ZipExtraField[] parseExtraFields(final byte[] data, final boolean local, final ExtraFieldParsingBehavior parsingBehavior) throws ZipException {
        if (extraFieldFactory != null) {
            return ExtraFieldUtils.parse(data, local, new ExtraFieldParsingBehavior() {
                @Override
                public ZipExtraField createExtraField(final ZipShort headerId) throws ZipException, InstantiationException, IllegalAccessException {
                    final ZipExtraField field = extraFieldFactory.apply(headerId);
                    return field == null ? parsingBehavior.createExtraField(headerId) : field;
                }

                @Override
                public ZipExtraField fill(final ZipExtraField field, final byte[] data, final int off, final int len, final boolean local) throws ZipException {
                    return parsingBehavior.fill(field, data, off, len, local);
                }

                @Override
                public ZipExtraField onUnparseableExtraField(final byte[] data, final int off, final int len, final boolean local, final int claimedLength)
                        throws ZipException {
                    return parsingBehavior.onUnparseableExtraField(data, off, len, local, claimedLength);
                }
            });
        }
        return ExtraFieldUtils.parse(data, local, parsingBehavior);
    }

    /// Removes an extra field, reapplying remaining timestamps if a timestamp field is removed.
    ///
    /// @param type the type of extra field to remove
    /// @throws NoSuchElementException if no field with this type exists
    public void removeExtraField(final ZipShort type) {
        final ZipExtraField removed = getExtraField(type);
        if (removed == null) {
            throw new NoSuchElementException();
        }
        internalRemoveExtraField(type);
        setExtra();
        updateTimeFieldsFromExtraField(removed);
    }

    /**
     * Removes unparseable extra field data.
     *
     * @since 1.1
     */
    public void removeUnparseableExtraFieldData() {
        if (unparseableExtra == null) {
            throw new NoSuchElementException();
        }
        unparseableExtra = null;
        setExtra();
    }

    /**
     * Sets alignment for this entry.
     *
     * @param alignment requested alignment, 0 for default.
     * @since 1.14
     */
    public void setAlignment(final int alignment) {
        if ((alignment & alignment - 1) != 0 || alignment > 0xffff) {
            throw new IllegalArgumentException("Invalid value for alignment, must be power of two and no bigger than " + 0xffff + " but is " + alignment);
        }
        this.alignment = alignment;
    }

    private void setAttributes(final Path inputPath, final LinkOption... options) throws IOException {
        final BasicFileAttributes attributes = Files.readAttributes(inputPath, BasicFileAttributes.class, options);
        if (attributes.isRegularFile()) {
            setSize(attributes.size());
        }
        internalSetLastModifiedTime(attributes.lastModifiedTime());
        creationTime = attributes.creationTime();
        lastAccessTime = attributes.lastAccessTime();
        setExtraTimeFields();
    }

    /**
     * Sets the central directory part of extra fields.
     *
     * @param b an array of bytes to be parsed into extra fields
     */
    public void setCentralDirectoryExtra(final byte[] b) {
        try {
            mergeExtraFields(parseExtraFields(b, false, ExtraFieldParsingMode.BEST_EFFORT), false);
        } catch (final ZipException e) {
            // actually this is not possible as of Commons Compress 1.19
            throw new IllegalArgumentException(e.getMessage(), e); // NOSONAR
        }
    }

    /**
     * Sets the source of the comment field value.
     *
     * @param commentSource source of the comment field value
     * @since 1.16
     */
    public void setCommentSource(final CommentSource commentSource) {
        this.commentSource = commentSource;
    }

    /// Sets the entry comment.
    ///
    /// @param comment the comment, or null to clear it
    /// @throws IllegalArgumentException if the comment exceeds 65535 characters
    public void setComment(final String comment) {
        if (comment != null && comment.length() > 0xffff) {
            throw new IllegalArgumentException("Entry comment exceeds 65535 characters");
        }
        this.comment = comment;
    }

    /// Sets the compressed size in bytes. No range validation is performed.
    ///
    /// @param size the compressed size, or -1 if unknown
    public void setCompressedSize(final long size) {
        compressedSize = size;
    }

    /// Sets the CRC-32 value of the uncompressed data.
    ///
    /// @param crc the unsigned CRC-32 value
    /// @throws IllegalArgumentException if crc is outside the range 0 to 0xffffffff
    public void setCrc(final long crc) {
        if (crc < 0 || crc > 0xffffffffL) {
            throw new IllegalArgumentException("Invalid entry CRC-32: " + crc);
        }
        this.crc = crc;
    }

    /// Sets the creation time and updates the timestamp extra fields.
    ///
    /// @param time the creation time, not null
    /// @return this entry
    /// @throws NullPointerException if time is null
    /// @throws IllegalArgumentException if the updated extra data exceeds 65535 bytes
    public ZipArchiveEntry setCreationTime(final FileTime time) {
        setExtraTimeFields(xdostime, lastModifiedTime, lastAccessTime, Objects.requireNonNull(time, "time"));
        return this;
    }

    /**
     * Sets the data offset.
     *
     * @param dataOffset new value of data offset.
     */
    protected void setDataOffset(final long dataOffset) {
        this.dataOffset = dataOffset;
    }

    /**
     * The number of the split segment this entry starts at.
     *
     * @param diskNumberStart the number of the split segment this entry starts at.
     * @since 1.20
     */
    public void setDiskNumberStart(final long diskNumberStart) {
        this.diskNumberStart = diskNumberStart;
    }

    /**
     * Sets the external file attributes.
     *
     * @param value an {@code long} value
     */
    public void setExternalAttributes(final long value) {
        externalAttributes = value;
    }

    /// Serializes the current local extra fields.
    protected void setExtra() {
        final byte[] data = ExtraFieldUtils.mergeLocalFileDataData(getAllExtraFieldsNoCopy());
        if (data.length > 0xffff) {
            throw new IllegalArgumentException("Extra data exceeds 65535 bytes");
        }
        extra = data;
    }

    /// Parses and merges extra field data, retaining unparseable data as [UnparseableExtraFieldData].
    ///
    /// @param extra an array of bytes to be parsed into extra fields, or null
    /// @throws IllegalArgumentException if the input or merged extra data exceeds 65535 bytes, or parsing fails
    public void setExtra(final byte[] extra) throws RuntimeException {
        if (extra != null && extra.length > 0xffff) {
            throw new IllegalArgumentException("Extra data exceeds 65535 bytes");
        }
        try {
            mergeExtraFields(parseExtraFields(extra, true, ExtraFieldParsingMode.BEST_EFFORT), true);
        } catch (final ZipException e) {
            // actually this is not possible as of Commons Compress 1.1
            throw new IllegalArgumentException("Error parsing extra fields for entry: " // NOSONAR
                                               + getName() + " - " + e.getMessage(), e);
        }
    }

    /**
     * Replaces all currently attached extra fields with the new array.
     *
     * @param fields an array of extra fields
     */
    public void setExtraFields(final ZipExtraField[] fields) {
        unparseableExtra = null;
        final List<ZipExtraField> newFields = new ArrayList<>();
        if (fields != null) {
            for (final ZipExtraField field : fields) {
                if (field instanceof UnparseableExtraFieldData) {
                    unparseableExtra = (UnparseableExtraFieldData) field;
                } else {
                    newFields.add(field);
                }
            }
        }
        extraFields = newFields.toArray(ExtraFieldUtils.EMPTY_ZIP_EXTRA_FIELD_ARRAY);
        setExtra();
        updateTimeFieldsFromExtraFields();
    }

    /// Regenerates timestamp extra fields from the current times.
    private void setExtraTimeFields() {
        setExtraTimeFields(xdostime, lastModifiedTime, lastAccessTime, creationTime);
    }

    /// Validates the replacement extra data before committing the supplied times and fields.
    private void setExtraTimeFields(final long xdostime, final FileTime lastModifiedTime,
                                   final FileTime lastAccessTime, final FileTime creationTime) {
        final List<ZipExtraField> fields = new ArrayList<>();
        if (extraFields != null) {
            for (final ZipExtraField field : extraFields) {
                final ZipShort id = field.getHeaderId();
                if (!X5455_ExtendedTimestamp.HEADER_ID.equals(id) && !X000A_NTFS.HEADER_ID.equals(id)) {
                    fields.add(field);
                }
            }
        }
        if (lastModifiedTime != null || lastAccessTime != null || creationTime != null) {
            if (canConvertToInfoZipExtendedTimestamp(lastModifiedTime, lastAccessTime, creationTime)) {
                fields.add(createInfoZipExtendedTimestamp(lastModifiedTime, lastAccessTime, creationTime));
            }
            fields.add(createNTFSTimestamp(lastModifiedTime, lastAccessTime, creationTime));
        }
        final ZipExtraField[] newFields = fields.toArray(ExtraFieldUtils.EMPTY_ZIP_EXTRA_FIELD_ARRAY);
        if (unparseableExtra != null) {
            fields.add(unparseableExtra);
        }
        final byte[] data = ExtraFieldUtils.mergeLocalFileDataData(unparseableExtra == null ? newFields
                : fields.toArray(ExtraFieldUtils.EMPTY_ZIP_EXTRA_FIELD_ARRAY));
        if (data.length > 0xffff) {
            throw new IllegalArgumentException("Extra data exceeds 65535 bytes");
        }
        this.xdostime = xdostime;
        this.lastModifiedTime = lastModifiedTime;
        this.lastAccessTime = lastAccessTime;
        this.creationTime = creationTime;
        extraFields = newFields;
        extra = data;
    }

    /**
     * Sets the "general purpose bit" field.
     *
     * @param generalPurposeBit the general purpose bit
     * @since 1.1
     */
    public void setGeneralPurposeBit(final GeneralPurposeBit generalPurposeBit) {
        this.generalPurposeBit = generalPurposeBit;
    }

    /**
     * Sets the internal file attributes.
     *
     * @param internalAttributes an {@code int} value
     */
    public void setInternalAttributes(final int internalAttributes) {
        this.internalAttributes = internalAttributes;
    }

    /// Sets the last access time and updates the timestamp extra fields.
    ///
    /// @param fileTime the last access time, not null
    /// @return this entry
    /// @throws NullPointerException if fileTime is null
    /// @throws IllegalArgumentException if the updated extra data exceeds 65535 bytes
    public ZipArchiveEntry setLastAccessTime(final FileTime fileTime) {
        setExtraTimeFields(xdostime, lastModifiedTime, Objects.requireNonNull(fileTime, "fileTime"), creationTime);
        return this;
    }

    /// Sets the modification time and records it in timestamp extra fields.
    ///
    /// @param fileTime the modification time, not null
    /// @return this entry
    /// @throws NullPointerException if fileTime is null
    /// @throws IllegalArgumentException if the updated extra data exceeds 65535 bytes
    public ZipArchiveEntry setLastModifiedTime(final FileTime fileTime) {
        Objects.requireNonNull(fileTime, "fileTime");
        final long dosTime = toExtendedDosTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(fileTime.toMillis()), ZoneId.systemDefault()));
        setExtraTimeFields(dosTime, fileTime, lastAccessTime, creationTime);
        return this;
    }

    protected void setLocalHeaderOffset(final long localHeaderOffset) {
        this.localHeaderOffset = localHeaderOffset;
    }

    /**
     * Sets the compression method of this entry.
     *
     * @param method compression method
     * @since 1.1
     */
    public void setMethod(final int method) {
        if (method < 0) {
            throw new IllegalArgumentException("ZIP compression method cannot be negative: " + method);
        }
        this.method = method;
    }

    /// Sets the entry name, normalizing backslashes on the FAT platform when no `/` is present.
    ///
    /// @param name the name to use, not null
    /// @throws NullPointerException if name is null
    /// @throws IllegalArgumentException if name exceeds 65535 characters
    protected void setName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.length() > 0xffff) {
            throw new IllegalArgumentException("Entry name exceeds 65535 characters");
        }
        if (getPlatform() == PLATFORM_FAT && !name.contains(ZIP_DIR_SEP)) {
            name = name.replace('\\', '/');
        }
        this.name = name;
    }

    /**
     * Sets the name using the raw bytes and the string created from it by guessing or using the configured encoding.
     *
     * @param name    the name to use created from the raw bytes using the guessed or configured encoding
     * @param rawName the bytes originally read as name from the archive
     * @since 1.2
     */
    protected void setName(final String name, final byte[] rawName) {
        setName(name);
        this.rawName = rawName;
    }

    /**
     * Sets the source of the name field value.
     *
     * @param nameSource source of the name field value
     * @since 1.16
     */
    public void setNameSource(final NameSource nameSource) {
        this.nameSource = nameSource;
    }

    /**
     * Sets the platform (UNIX or FAT).
     *
     * @param platform an {@code int} value - 0 is FAT, 3 is Unix
     */
    protected void setPlatform(final int platform) {
        this.platform = platform;
    }

    /**
     * Sets the content of the flags field.
     *
     * @param rawFlag content of the flags field
     * @since 1.11
     */
    public void setRawFlag(final int rawFlag) {
        this.rawFlag = rawFlag;
    }

    /**
     * Sets the uncompressed size of the entry data.
     *
     * @param size the uncompressed size in bytes
     * @throws IllegalArgumentException if the specified size is less than 0
     */
    public void setSize(final long size) {
        if (size < 0) {
            throw new IllegalArgumentException("Invalid entry size");
        }
        this.size = size;
    }

    protected void setStreamContiguous(final boolean isStreamContiguous) {
        this.isStreamContiguous = isStreamContiguous;
    }

    /**
     * Sets the modification time of the entry.
     *
     * @param fileTime the entry modification time.
     * @since 1.21
     */
    public void setTime(final FileTime fileTime) {
        setTime(fileTime.toMillis());
    }

    /// Sets the modification time and updates the timestamp extra fields.
    /// Times in local years 1980 through 2099 are stored as DOS local times;
    /// other times are also recorded in timestamp extra fields.
    ///
    /// @param timeEpochMillis the last modification time in milliseconds since the epoch
    /// @throws IllegalArgumentException if the updated extra data exceeds 65535 bytes
    /// @see #getTime()
    /// @see #setLastModifiedTime(FileTime)
    public void setTime(final long timeEpochMillis) {
        final LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(timeEpochMillis), ZoneId.systemDefault());
        final FileTime modified = time.getYear() >= 1980 && time.getYear() <= 2099 ? null : FileTime.fromMillis(timeEpochMillis);
        setExtraTimeFields(toExtendedDosTime(time), modified, lastAccessTime, creationTime);
    }

    /// Sets the local modification time with millisecond precision and updates timestamp extra fields.
    /// Times outside the DOS year range 1980 through 2107, and exactly 1980-01-01T00:00,
    /// are also stored as absolute timestamps using the system default time zone.
    /// Gaps are shifted forward and overlaps use the earlier offset.
    ///
    /// @param time the local modification time, not null
    /// @throws NullPointerException if time is null
    /// @throws IllegalArgumentException if the updated extra data exceeds 65535 bytes
    public void setTimeLocal(final LocalDateTime time) {
        Objects.requireNonNull(time, "time");
        final long dosTime = toExtendedDosTime(time);
        final FileTime modified = dosTime != DOSTIME_BEFORE_1980 && time.getYear() <= 2107 ? null
                : FileTime.from(time.withNano(time.getNano() / 1_000_000 * 1_000_000)
                        .atZone(ZoneId.systemDefault()).toInstant());
        setExtraTimeFields(dosTime, modified, lastAccessTime, creationTime);
    }

    /// Sets the DOS modification time read from an archive, using only the low 32 bits.
    void setDosTime(final long time) {
        xdostime = time & 0xffffffffL;
        lastModifiedTime = null;
    }

    /// Encodes the low seven bits of the DOS year and stores the millisecond remainder in the high 32 bits.
    /// Dates before 1980 use [#DOSTIME_BEFORE_1980].
    private static long toExtendedDosTime(final LocalDateTime time) {
        if (time.getYear() < 1980) {
            return DOSTIME_BEFORE_1980;
        }
        final long dosTime = (long) (time.getYear() - 1980) << 25 | time.getMonthValue() << 21
                | time.getDayOfMonth() << 16 | time.getHour() << 11 | time.getMinute() << 5 | time.getSecond() >> 1;
        final long millis = (time.getSecond() & 1) * 1000 + time.getNano() / 1_000_000;
        return (dosTime & 0xffffffffL) | millis << 32;
    }

    /// Returns the entry name.
    @Override
    public String toString() {
        return getName();
    }

    /**
     * Sets Unix permissions in a way that is understood by Info-Zip's unzip command.
     *
     * @param mode an {@code int} value
     */
    public void setUnixMode(final int mode) {
        // CheckStyle:MagicNumberCheck OFF - no point
        setExternalAttributes(mode << SHORT_SHIFT
                              // MS-DOS read-only attribute
                              | ((mode & 0200) == 0 ? 1 : 0)
                              // MS-DOS directory flag
                              | (isDirectory() ? 0x10 : 0));
        // CheckStyle:MagicNumberCheck ON
        platform = PLATFORM_UNIX;
    }

    /**
     * Sets the "version made by" field.
     *
     * @param versionMadeBy "version made by" field
     * @since 1.11
     */
    public void setVersionMadeBy(final int versionMadeBy) {
        this.versionMadeBy = versionMadeBy;
    }

    /**
     * Sets the "version required to expand" field.
     *
     * @param versionRequired "version required to expand" field
     * @since 1.11
     */
    public void setVersionRequired(final int versionRequired) {
        this.versionRequired = versionRequired;
    }

    /// Tests whether the field contains supported timestamps.
    private static boolean isTimestampExtraField(final ZipExtraField field) {
        return field instanceof X5455_ExtendedTimestamp || field instanceof X000A_NTFS;
    }

    /// Refreshes timestamps when a timestamp extra field has changed.
    private void updateTimeFieldsFromExtraField(final ZipExtraField field) {
        if (isTimestampExtraField(field)) {
            updateTimeFieldsFromExtraFields();
        }
    }

    /// Reads timestamp extra fields, giving NTFS values precedence over extended timestamps.
    private void updateTimeFieldsFromExtraFields() {
        // Update times from X5455_ExtendedTimestamp field
        updateTimeFromExtendedTimestampField();
        // Update times from X000A_NTFS field, overriding X5455_ExtendedTimestamp if both are present
        updateTimeFromNtfsField();
    }

    /// Reads available times from the extended timestamp field.
    private void updateTimeFromExtendedTimestampField() {
        final ZipExtraField extraField = getExtraField(X5455_ExtendedTimestamp.HEADER_ID);
        if (extraField instanceof X5455_ExtendedTimestamp) {
            final X5455_ExtendedTimestamp extendedTimestamp = (X5455_ExtendedTimestamp) extraField;
            if (extendedTimestamp.isBit0_modifyTimePresent()) {
                final FileTime modifyTime = extendedTimestamp.getModifyFileTime();
                if (modifyTime != null) {
                    internalSetLastModifiedTime(modifyTime);
                }
            }
            if (extendedTimestamp.isBit1_accessTimePresent()) {
                final FileTime accessTime = extendedTimestamp.getAccessFileTime();
                if (accessTime != null) {
                    lastAccessTime = accessTime;
                }
            }
            if (extendedTimestamp.isBit2_createTimePresent()) {
                final FileTime creationTime = extendedTimestamp.getCreateFileTime();
                if (creationTime != null) {
                    this.creationTime = creationTime;
                }
            }
        }
    }

    /// Reads available NTFS times with 100-nanosecond precision.
    private void updateTimeFromNtfsField() {
        final ZipExtraField extraField = getExtraField(X000A_NTFS.HEADER_ID);
        if (extraField instanceof X000A_NTFS) {
            final X000A_NTFS ntfsTimestamp = (X000A_NTFS) extraField;
            final FileTime modifyTime = ntfsTimestamp.getModifyFileTime();
            if (modifyTime != null) {
                internalSetLastModifiedTime(modifyTime);
            }
            final FileTime accessTime = ntfsTimestamp.getAccessFileTime();
            if (accessTime != null) {
                lastAccessTime = accessTime;
            }
            final FileTime creationTime = ntfsTimestamp.getCreateFileTime();
            if (creationTime != null) {
                this.creationTime = creationTime;
            }
        }
    }
}
