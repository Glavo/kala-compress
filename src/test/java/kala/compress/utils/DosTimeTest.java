/*
 * Copyright 2026 Glavo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kala.compress.utils;

import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;

import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Tests DOS timestamp normalization and time-zone resolution.
@Isolated("Changes the system default time zone")
public class DosTimeTest {

    /// Checks explicit date boundaries, field overflow, and time-zone transitions.
    ///
    /// @param zoneId the default time-zone ID
    /// @param year the encoded year
    /// @param month the encoded month
    /// @param day the encoded day
    /// @param hour the encoded hour
    /// @param minute the encoded minute
    /// @param second the encoded even second
    /// @param expected the expected UTC instant
    @ParameterizedTest
    @CsvSource({
            "UTC, 1980, 0, 0, 0, 0, 0, 1979-11-30T00:00:00Z",
            "UTC, 1980, 1, 0, 0, 0, 0, 1979-12-31T00:00:00Z",
            "UTC, 1980, 1, 1, 0, 0, 0, 1980-01-01T00:00:00Z",
            "UTC, 2023, 2, 31, 0, 0, 0, 2023-03-03T00:00:00Z",
            "UTC, 2024, 2, 29, 0, 0, 0, 2024-02-29T00:00:00Z",
            "UTC, 2100, 2, 29, 0, 0, 0, 2100-03-01T00:00:00Z",
            "UTC, 2024, 13, 1, 0, 0, 0, 2025-01-01T00:00:00Z",
            "UTC, 2024, 12, 31, 31, 63, 62, 2025-01-01T08:04:02Z",
            "UTC, 2107, 15, 31, 31, 63, 62, 2108-04-01T08:04:02Z",
            "+05:45, 1980, 0, 0, 0, 0, 0, 1979-11-29T18:15:00Z",
            "-03:30, 2024, 12, 31, 31, 63, 62, 2025-01-01T11:34:02Z",
            "+18:00, 1980, 1, 1, 0, 0, 0, 1979-12-31T06:00:00Z",
            "-18:00, 2107, 15, 31, 31, 63, 62, 2108-04-02T02:04:02Z",
            "Etc/GMT+5, 2024, 2, 29, 0, 0, 0, 2024-02-29T05:00:00Z",
            "America/New_York, 2024, 3, 10, 2, 30, 0, 2024-03-10T07:30:00Z",
            "America/New_York, 2024, 11, 3, 1, 30, 0, 2024-11-03T06:30:00Z",
            "Australia/Lord_Howe, 2024, 10, 6, 2, 10, 0, 2024-10-05T15:40:00Z",
            "Australia/Lord_Howe, 2024, 4, 7, 1, 45, 0, 2024-04-06T15:15:00Z",
            "Pacific/Apia, 2011, 12, 30, 12, 0, 0, 2011-12-30T22:00:00Z"
    })
    public void testConversion(final String zoneId, final int year, final int month, final int day,
                               final int hour, final int minute, final int second, final String expected) {
        final ZoneId zone = ZoneId.of(zoneId);
        final long dosTime = (long) (year - 1980) << 25 | (long) month << 21 | (long) day << 16
                | (long) hour << 11 | (long) minute << 5 | second / 2;
        final Instant instant = Instant.parse(expected);
        assertEquals(instant.toEpochMilli(), TimeUtils.dosTimeToEpochMilli(dosTime, zone));
        assertEquals(instant.toEpochMilli(), TimeUtils.dosTimeToEpochMilli(dosTime | 0xFFFFFFFF00000000L, zone));

        final TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            assertEquals(FileTime.from(instant), TimeUtils.dosTimeToFileTime(dosTime));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    /// Compares arbitrary packed timestamps with lenient Gregorian calendar conversion.
    ///
    /// @param zoneId the time-zone ID used for conversion
    @ParameterizedTest
    @ValueSource(strings = {"UTC", "+05:45", "-03:30", "Etc/GMT+5", "Asia/Shanghai", "America/New_York",
            "Europe/Berlin", "Australia/Lord_Howe", "Pacific/Apia"})
    public void testCalendarCompatibility(final String zoneId) {
        final ZoneId zone = ZoneId.of(zoneId);
        final Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone(zone), Locale.ROOT);
        final Random random = new Random(0x444F53L);
        for (int i = 0; i < 4096; i++) {
            final long dosTime = random.nextLong();
            calendar.clear();
            calendar.set((int) (dosTime >>> 25 & 0x7F) + 1980, (int) (dosTime >>> 21 & 0x0F) - 1,
                    (int) (dosTime >>> 16 & 0x1F), (int) (dosTime >>> 11 & 0x1F),
                    (int) (dosTime >>> 5 & 0x3F), (int) (dosTime & 0x1F) * 2);
            assertEquals(calendar.getTimeInMillis(), TimeUtils.dosTimeToEpochMilli(dosTime, zone),
                    () -> "DOS timestamp: " + Long.toUnsignedString(dosTime) + ", zone: " + zoneId);
        }
    }
}
