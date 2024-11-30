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
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for handling time-related types and conversions.
 * <p>
 * Understanding UNIX vs NTFS timestamps:
 * </p>
 * <ul>
 * <li>A <a href="https://en.wikipedia.org/wiki/Unix_time">UNIX timestamp</a> is a primitive long starting at the UNIX Epoch on January 1st, 1970 at Coordinated
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
     * Universal Time (UTC). This is the offset of Windows time 0 to UNIX epoch in 100-nanosecond intervals.
     * </p>
     */
    static final long WINDOWS_EPOCH_OFFSET = -116444736000000000L;

    /**
     * Tests whether a FileTime can be safely represented in the standard UNIX time.
     *
     * <p>
     * TODO ? If the FileTime is null, this method always returns true.
     * </p>
     *
     * @param time the FileTime to evaluate, can be null.
     * @return true if the time exceeds the minimum or maximum UNIX time, false otherwise.
     */
    public static boolean isUnixTime(final FileTime time) {
        return isUnixTime(toUnixTime(time));
    }

    /**
     * Tests whether a given number of seconds (since Epoch) can be safely represented in the standard UNIX time.
     *
     * @param seconds the number of seconds (since Epoch) to evaluate.
     * @return true if the time can be represented in the standard UNIX time, false otherwise.
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
     * Converts {@link FileTime} to standard UNIX time.
     *
     * @param fileTime the original FileTime.
     * @return the UNIX timestamp.
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
     * Converts standard UNIX time (in seconds, UTC/GMT) to {@link FileTime}.
     *
     * @param time UNIX timestamp (in seconds, UTC/GMT).
     * @return the corresponding FileTime.
     */
    public static FileTime unixTimeToFileTime(final long time) {
        return FileTime.from(time, TimeUnit.SECONDS);
    }

    /**
     * Converts DOS time to Java time (number of milliseconds since epoch).
     *
     * @param dosTime time to convert
     * @return converted time
     * @since 1.27.1-0
     */
    public static FileTime dosTimeToFileTime(final long dosTime) {
        final Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, (int) (dosTime >> 25 & 0x7f) + 1980);
        cal.set(Calendar.MONTH, (int) (dosTime >> 21 & 0x0f) - 1);
        cal.set(Calendar.DATE, (int) (dosTime >> 16) & 0x1f);
        cal.set(Calendar.HOUR_OF_DAY, (int) (dosTime >> 11) & 0x1f);
        cal.set(Calendar.MINUTE, (int) (dosTime >> 5) & 0x3f);
        cal.set(Calendar.SECOND, (int) (dosTime << 1) & 0x3e);
        cal.set(Calendar.MILLISECOND, 0);
        return FileTime.fromMillis(cal.getTimeInMillis());
    }

    /** Private constructor to prevent instantiation of this utility class. */
    private TimeUtils() {
    }
}
