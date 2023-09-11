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

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Support for HTTP date formats based on <a href="https://tools.ietf.org/html/rfc2616">RFC2616</a>.
 */
public final class DateTime {
    /**
     * The RFC850 date-time formatter, such as {@code 'Sunday, 06-Nov-94 08:49:37 GMT'}.
     * <p>
     * This is <b>obsolete</b> standard (obsoleted by RFC1036). Headers MUST NOT be generated in this format.
     * However it should be used as a fallback for parsing to achieve compatibility with older HTTP standards.
     * <p>
     * Since the format accepts <b>2 digits year</b> representation formatter works well for dates between
     * {@code (now - 50 Years)} and {@code (now + 49 Years)}.
     */
    public static final DateTimeFormatter RFC_850_DATE_TIME = DateTimeHelper.RFC_850_DATE_TIME;
    /**
     * The RFC1123 date-time formatter, such as {@code 'Tue, 3 Jun 2008 11:05:30 GMT'}.
     * <p>
     * <b>This is standard for RFC2616 and all created headers MUST be in this format!</b> However implementation must
     * accept headers also in RFC850 and <i>ANSI C</i> {@code asctime()} format.
     * <p>
     * This is just copy of convenient copy of {@link java.time.format.DateTimeFormatter#RFC_1123_DATE_TIME}.
     */
    public static final DateTimeFormatter RFC_1123_DATE_TIME = DateTimeFormatter.RFC_1123_DATE_TIME;
    /**
     * The <i>ANSI C's</i> {@code asctime()} format, such as {@code 'Sun Nov  6 08:49:37 1994'}.
     * <p>
     * Headers MUST NOT be generated in this format.
     * However it should be used as a fallback for parsing to achieve compatibility with older HTTP standards.
     */
    public static final DateTimeFormatter ASCTIME_DATE_TIME = DateTimeHelper.ASCTIME_DATE_TIME;

    private DateTime() {
    }

    /**
     * Parse provided text to {@link java.time.ZonedDateTime} using any possible date / time format specified
     * by <a href="https://tools.ietf.org/html/rfc2616">RFC2616 Hypertext Transfer Protocol</a>.
     * <p>
     * Formats are specified by {@link #RFC_1123_DATE_TIME}, {@link #RFC_850_DATE_TIME} and {@link #ASCTIME_DATE_TIME}.
     *
     * @param text a text to parse.
     * @return parsed date time.
     * @throws java.time.format.DateTimeParseException if not in any of supported formats.
     */
    public static ZonedDateTime parse(String text) {
        return DateTimeHelper.parse(text);
    }

    /**
     * Last recorded timestamp.
     *
     * @return timestamp
     */
    public static ZonedDateTime timestamp() {
        return DateTimeHelper.timestamp();
    }

    /**
     * Get current time as RFC-1123 string.
     *
     * @return formatted current time
     * @see #RFC_1123_DATE_TIME
     */
    public static String rfc1123String() {
        return DateTimeHelper.rfc1123String();
    }

    /**
     * Formatted date time terminated by carriage return and new line.
     *
     * @return date bytes for HTTP/1
     */
    public static byte[] http1Bytes() {
        return DateTimeHelper.http1Bytes();
    }
}
