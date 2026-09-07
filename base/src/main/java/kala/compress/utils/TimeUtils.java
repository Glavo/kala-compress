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
package kala.compress.utils;

import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for handling time-related types and conversions.
 * <p>
 * Understanding Unix vs NTFS timestamps:
 * </p>
 * <ul>
 * <li>A <a href="https://en.wikipedia.org/wiki/Unix_time">Unix timestamp</a> is a primitive long starting at the Unix Epoch on January 1st, 1970 at Coordinated
 * Universal Time (UTC)</li>
 * <li>An <a href="https://learn.microsoft.com/en-us/windows/win32/sysinfo/file-times">NTFS timestamp</a> is a file time is a 64-bit value that represents the
 * number of 100-nanosecond intervals that have elapsed since 12:00 A.M. January 1, 1601 Coordinated Universal Time (UTC).</li>
 * </ul>
 *
 * @since 1.23
 */
public final class TimeUtils {

    /** The amount of 100-nanosecond intervals in one millisecond. */
    static final long HUNDRED_NANOS_PER_MILLISECOND = TimeUnit.MILLISECONDS.toNanos(1) / 100;

    /** The amount of 100-nanosecond intervals in one second. */
    private static final long HUNDRED_NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1) / 100;

    /**
     * <a href="https://msdn.microsoft.com/en-us/library/windows/desktop/ms724290%28v=vs.85%29.aspx">Windows File Times</a>
     * <p>
     * A file time is a 64-bit value that represents the number of 100-nanosecond intervals that have elapsed since 12:00 A.M. January 1, 1601 Coordinated
     * Universal Time (UTC). This is the offset of Windows time 0 to Unix epoch in 100-nanosecond intervals.
     * </p>
     */
    static final long WINDOWS_EPOCH_OFFSET = -116444736000000000L;

    /**
     * Tests whether a FileTime can be safely represented in the standard Unix time.
     *
     * <p>
     * TODO ? If the FileTime is null, this method always returns true.
     * </p>
     *
     * @param time the FileTime to evaluate, can be null.
     * @return true if the time exceeds the minimum or maximum Unix time, false otherwise.
     */
    public static boolean isUnixTime(final FileTime time) {
        return isUnixTime(toUnixTime(time));
    }

    /**
     * Tests whether a given number of seconds (since Epoch) can be safely represented in the standard Unix time.
     *
     * @param seconds the number of seconds (since Epoch) to evaluate.
     * @return true if the time can be represented in the standard Unix time, false otherwise.
     */
    public static boolean isUnixTime(final long seconds) {
        return Integer.MIN_VALUE <= seconds && seconds <= Integer.MAX_VALUE;
    }

    /**
     * Converts NTFS time (100-nanosecond units since 1 January 1601) to a FileTime.
     *
     * @param ntfsTime the NTFS time in 100-nanosecond units.
     * @return the FileTime.
     */
    public static FileTime ntfsTimeToFileTime(final long ntfsTime) {
        final long javaHundredsNanos = Math.addExact(ntfsTime, WINDOWS_EPOCH_OFFSET);
        final long javaSeconds = Math.floorDiv(javaHundredsNanos, HUNDRED_NANOS_PER_SECOND);
        final long javaNanos = Math.floorMod(javaHundredsNanos, HUNDRED_NANOS_PER_SECOND) * 100;
        return FileTime.from(Instant.ofEpochSecond(javaSeconds, javaNanos));
    }

    /**
     * Converts a {@link FileTime} to NTFS time (100-nanosecond units since 1 January 1601).
     *
     * @param fileTime the FileTime.
     * @return the NTFS time in 100-nanosecond units.
     */
    public static long toNtfsTime(final FileTime fileTime) {
        final Instant instant = fileTime.toInstant();
        final long javaHundredNanos = instant.getEpochSecond() * HUNDRED_NANOS_PER_SECOND + instant.getNano() / 100;
        return Math.subtractExact(javaHundredNanos, WINDOWS_EPOCH_OFFSET);
    }

    /**
     * Converts Java time (milliseconds since Epoch) to NTFS time.
     *
     * @param javaTime the Java time.
     * @return the NTFS time.
     */
    public static long toNtfsTime(final long javaTime) {
        final long javaHundredNanos = javaTime * HUNDRED_NANOS_PER_MILLISECOND;
        return Math.subtractExact(javaHundredNanos, WINDOWS_EPOCH_OFFSET);
    }

    /**
     * Converts {@link FileTime} to standard Unix time.
     *
     * @param fileTime the original FileTime.
     * @return the Unix timestamp.
     */
    public static long toUnixTime(final FileTime fileTime) {
        return fileTime != null ? fileTime.to(TimeUnit.SECONDS) : 0;
    }

    /**
     * Truncates a FileTime to 100-nanosecond precision.
     *
     * @param fileTime the FileTime to be truncated.
     * @return the truncated FileTime.
     */
    public static FileTime truncateToHundredNanos(final FileTime fileTime) {
        final Instant instant = fileTime.toInstant();
        return FileTime.from(Instant.ofEpochSecond(instant.getEpochSecond(), instant.getNano() / 100 * 100));
    }

    /**
     * Converts standard Unix time (in seconds, UTC/GMT) to {@link FileTime}.
     *
     * @param time Unix timestamp (in seconds, UTC/GMT).
     * @return the corresponding FileTime.
     */
    public static FileTime unixTimeToFileTime(final long time) {
        return FileTime.from(time, TimeUnit.SECONDS);
    }

    /// Converts a packed DOS timestamp to milliseconds since `1970-01-01T00:00:00Z`.
    ///
    /// Only the low 32 bits are used. The fields are interpreted in the specified
    /// time zone, with a year offset from 1980 and a two-second time resolution.
    /// Out-of-range fields are normalized by adding the month minus one, day minus
    /// one, and time of day to January 1 of the encoded year. For example, day zero
    /// denotes the last day of the preceding month.
    ///
    /// A local time in a time-zone gap is shifted forward by the gap's length.
    /// A local time in an overlap uses the later offset.
    ///
    /// @param dosTime the packed DOS timestamp
    /// @param zone the time zone used to interpret the timestamp, not null
    /// @return the number of milliseconds since the epoch
    /// @throws NullPointerException if zone is null
    /// @since 1.27.1-5
    public static long dosTimeToEpochMilli(final long dosTime, final ZoneId zone) {
        final int year = (int) ((dosTime >>> 25) & 0x7F) + 1980;
        final int month = (int) ((dosTime >>> 21) & 0x0F);
        final int day = (int) ((dosTime >>> 16) & 0x1F);
        final int hour = (int) ((dosTime >>> 11) & 0x1F);
        final int minute = (int) ((dosTime >>> 5) & 0x3F);
        final int second = (int) (dosTime & 0x1F) * 2;

        final int seconds = hour * 3600 + minute * 60 + second;
        final LocalDate date = LocalDate.of(year + Math.floorDiv(month - 1, 12), Math.floorMod(month - 1, 12) + 1, 1)
                .plusDays(day - 1L + seconds / 86400);
        final LocalTime time = LocalTime.ofSecondOfDay(seconds % 86400);

        return ZonedDateTime.of(date, time, zone)
                .withLaterOffsetAtOverlap()
                .toEpochSecond() * 1000;
    }

    /// Converts a packed DOS timestamp to a file time in the system default time zone.
    ///
    /// Conversion follows [#dosTimeToEpochMilli(long, ZoneId)].
    ///
    /// @param dosTime the packed DOS timestamp
    /// @return the converted file time
    /// @since 1.27.1-0
    public static FileTime dosTimeToFileTime(final long dosTime) {
        return FileTime.fromMillis(dosTimeToEpochMilli(dosTime, ZoneId.systemDefault()));
    }

    /** Private constructor to prevent instantiation of this utility class. */
    private TimeUtils() {
    }
}
