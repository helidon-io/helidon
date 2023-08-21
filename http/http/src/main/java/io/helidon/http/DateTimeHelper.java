/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.http;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.SignStyle;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.DAY_OF_WEEK;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.OFFSET_SECONDS;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.time.temporal.ChronoField.YEAR;

// utility class to remove lines from Http class
final class DateTimeHelper {
    static final DateTimeFormatter RFC_850_DATE_TIME;
    static final DateTimeFormatter ASCTIME_DATE_TIME;

    private static volatile ZonedDateTime time;
    private static volatile String rfc1123String;
    private static volatile byte[] http1valueBytes;

    static {
        Map<Long, String> monthName3d = Map.ofEntries(Map.entry(1L, "Jan"),
                                                        Map.entry(2L, "Feb"),
                                                        Map.entry(3L, "Mar"),
                                                        Map.entry(4L, "Apr"),
                                                        Map.entry(5L, "May"),
                                                        Map.entry(6L, "Jun"),
                                                        Map.entry(7L, "Jul"),
                                                        Map.entry(8L, "Aug"),
                                                        Map.entry(9L, "Sep"),
                                                        Map.entry(10L, "Oct"),
                                                        Map.entry(11L, "Nov"),
                                                        Map.entry(12L, "Dec"));

        // manually code maps to ensure correct data always used
        // (locale data can be changed by application code)
        Map<Long, String> dayOfWeekFull = Map.of(1L, "Monday",
                                                 2L, "Tuesday",
                                                 3L, "Wednesday",
                                                 4L, "Thursday",
                                                 5L, "Friday",
                                                 6L, "Saturday",
                                                 7L, "Sunday");
        RFC_850_DATE_TIME = new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .parseLenient()
                .optionalStart()
                .appendText(DAY_OF_WEEK, dayOfWeekFull)
                .appendLiteral(", ")
                .optionalEnd()
                .appendValue(DAY_OF_MONTH, 2, 2, SignStyle.NOT_NEGATIVE)
                .appendLiteral('-')
                .appendText(MONTH_OF_YEAR, monthName3d)
                .appendLiteral('-')
                .appendValueReduced(YEAR, 2, 2, LocalDate.now().minusYears(50).getYear())
                .appendLiteral(' ')
                .appendValue(HOUR_OF_DAY, 2)
                .appendLiteral(':')
                .appendValue(MINUTE_OF_HOUR, 2)
                .optionalStart()
                .appendLiteral(':')
                .appendValue(SECOND_OF_MINUTE, 2)
                .optionalEnd()
                .appendLiteral(' ')
                .appendOffset("+HHMM", "GMT")
                .toFormatter();

        // manually code maps to ensure correct data always used
        // (locale data can be changed by application code)
        Map<Long, String> dayOfWeek3d = Map.of(1L, "Mon",
                                               2L, "Tue",
                                               3L, "Wed",
                                               4L, "Thu",
                                               5L, "Fri",
                                               6L, "Sat",
                                               7L, "Sun");
        ASCTIME_DATE_TIME = new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .parseLenient()
                .optionalStart()
                .appendText(DAY_OF_WEEK, dayOfWeek3d)
                .appendLiteral(' ')
                .appendText(MONTH_OF_YEAR, monthName3d)
                .appendLiteral(' ')
                .padNext(2)
                .appendValue(DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
                .appendLiteral(' ')
                .appendValue(HOUR_OF_DAY, 2)
                .appendLiteral(':')
                .appendValue(MINUTE_OF_HOUR, 2)
                .appendLiteral(':')
                .appendValue(SECOND_OF_MINUTE, 2)
                .appendLiteral(' ')
                .appendValue(YEAR, 4)
                .parseDefaulting(OFFSET_SECONDS, 0)
                .toFormatter();

        DateTimeHelper.update();

        // start a timer, scheduled every second to update server time (we do not need better precision)
        new Timer("helidon-http-timer", true)
                .schedule(new TimerTask() {
                    public void run() {
                        DateTimeHelper.update();
                    }
                }, 1000, 1000);
    }

    private DateTimeHelper() {
    }

    static ZonedDateTime parse(String text) {
        try {
            return ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME);
        } catch (DateTimeParseException pe) {
            try {
                return ZonedDateTime.parse(text, RFC_850_DATE_TIME);
            } catch (DateTimeParseException pe2) {
                return ZonedDateTime.parse(text, ASCTIME_DATE_TIME);
            }
        }
    }

    static ZonedDateTime timestamp() {
        return time;
    }

    static String rfc1123String() {
        return rfc1123String;
    }

    static byte[] http1Bytes() {
        return http1valueBytes;
    }

    static void update() {
        time = ZonedDateTime.now();
        rfc1123String = time.format(DateTimeFormatter.RFC_1123_DATE_TIME);
        http1valueBytes = (rfc1123String + "\r\n").getBytes(StandardCharsets.US_ASCII);
    }
}
