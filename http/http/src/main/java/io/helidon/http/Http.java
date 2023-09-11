/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.LazyString;
import io.helidon.common.mapper.Value;

/**
 * HTTP protocol related constants and utilities.
 * <p>
 * <b>Utility class</b>
 */
public final class Http {

    private Http() {
    }

    /**
     * HTTP Header with {@link HeaderName} and value.
     *
     * @see io.helidon.http.Http.Headers
     */
    public interface Header extends Value<String> {

        /**
         * Name of the header as configured by user
         * or as received on the wire.
         *
         * @return header name, always lower case for HTTP/2 headers
         */
        @Override
        String name();

        /**
         * Value of the header.
         *
         * @return header value
         * @deprecated use {@link #get()}
         */
        @Deprecated(forRemoval = true, since = "4.0.0")
        default String value() {
            return get();
        }

        /**
         * Header name for the header.
         *
         * @return header name
         */
        HeaderName headerName();

        /**
         * All values concatenated using a comma.
         *
         * @return all values joined by a comma
         */
        default String values() {
            return String.join(",", allValues());
        }

        /**
         * All values of this header.
         *
         * @return all configured values
         */
        List<String> allValues();

        /**
         * All values of this header. If this header is defined as a single header with comma separated values,
         * set {@code split} to true.
         *
         * @param split whether to split single value by comma, does nothing if the value is already a list.
         * @return list of values
         */
        default List<String> allValues(boolean split) {
            if (split) {
                List<String> values = allValues();
                if (values.size() == 1) {
                    String value = values.get(0);
                    if (value.contains(", ")) {
                        return List.of(value.split(", "));
                    } else {
                        return List.of(value);
                    }
                }
                return values;
            } else {
                return allValues();
            }
        }

        /**
         * Number of values this header has.
         *
         * @return number of values (minimal number is 1)
         */
        int valueCount();

        /**
         * Sensitive headers should not be logged, or indexed (HTTP/2).
         *
         * @return whether this header is sensitive
         */
        boolean sensitive();

        /**
         * Changing headers should not be cached, and their value should not be indexed (HTTP/2).
         *
         * @return whether this header's value is changing often
         */
        boolean changing();

        /**
         * Cached bytes of a single valued header's value.
         *
         * @return value bytes
         */
        default byte[] valueBytes() {
            return get().getBytes(StandardCharsets.US_ASCII);
        }

        /**
         * Write the current header as an HTTP header to the provided buffer.
         *
         * @param buffer buffer to write to (should be growing)
         */
        default void writeHttp1Header(BufferData buffer) {
            byte[] nameBytes = name().getBytes(StandardCharsets.US_ASCII);
            if (valueCount() == 1) {
                writeHeader(buffer, nameBytes, valueBytes());
            } else {
                for (String value : allValues()) {
                    writeHeader(buffer, nameBytes, value.getBytes(StandardCharsets.US_ASCII));
                }
            }
        }

        /**
         * Check validity of header name and values.
         *
         * @throws IllegalArgumentException in case the HeaderValue is not valid
         */
        default void validate() throws IllegalArgumentException {
            String name = name();
            // validate that header name only contains valid characters
            HttpToken.validate(name);
            // Validate header value
            validateValue(name, values());
        }


        // validate header value based on https://www.rfc-editor.org/rfc/rfc7230#section-3.2 and throws IllegalArgumentException
        // if invalid.
        private static void validateValue(String name, String value) throws IllegalArgumentException {
            char[] vChars = value.toCharArray();
            int vLength = vChars.length;
            for (int i = 0; i < vLength; i++) {
                char vChar = vChars[i];
                if (i == 0) {
                    if (vChar < '!' || vChar == '\u007f') {
                        throw new IllegalArgumentException("First character of the header value is invalid"
                                                                   + " for header '" + name + "'");
                    }
                } else {
                    if (vChar < ' ' && vChar != '\t' || vChar == '\u007f') {
                        throw new IllegalArgumentException("Character at position " + (i + 1) + " of the header value is invalid"
                                                                   + " for header '" + name + "'");
                    }
                }
            }
        }

        private void writeHeader(BufferData buffer, byte[] nameBytes, byte[] valueBytes) {
            // header name
            buffer.write(nameBytes);
            // ": "
            buffer.write(':');
            buffer.write(' ');
            // header value
            buffer.write(valueBytes);
            // \r\n
            buffer.write('\r');
            buffer.write('\n');
        }
    }

    /**
     * Mutable header value.
     */
    public interface HeaderValueWriteable extends Header {
        /**
         * Create a new mutable header from an existing header.
         *
         * @param header header to copy
         * @return a new mutable header
         */
        static HeaderValueWriteable create(Header header) {
            return new HeaderValueCopy(header);
        }

        /**
         * Add a value to this header.
         *
         * @param value value to add
         * @return this instance
         */
        HeaderValueWriteable addValue(String value);
    }

    /**
     * Values of commonly used headers.
     */
    public static final class Headers {
        /**
         * Accept byte ranges for file download.
         */
        public static final Header ACCEPT_RANGES_BYTES = createCached(HeaderNames.ACCEPT_RANGES, "bytes");
        /**
         * Not accepting byte ranges for file download.
         */
        public static final Header ACCEPT_RANGES_NONE = createCached(HeaderNames.ACCEPT_RANGES, "none");
        /**
         * Chunked transfer encoding.
         * Used in {@code HTTP/1}.
         */
        public static final Header TRANSFER_ENCODING_CHUNKED = createCached(HeaderNames.TRANSFER_ENCODING, "chunked");
        /**
         * Connection keep-alive.
         * Used in {@code HTTP/1}.
         */
        public static final Header CONNECTION_KEEP_ALIVE = createCached(HeaderNames.CONNECTION, "keep-alive");
        /**
         * Connection close.
         * Used in {@code HTTP/1}.
         */
        public static final Header CONNECTION_CLOSE = createCached(HeaderNames.CONNECTION, "close");
        /**
         * Content type application/json with no charset.
         */
        public static final Header CONTENT_TYPE_JSON = createCached(HeaderNames.CONTENT_TYPE, "application/json");
        /**
         * Content type text plain with no charset.
         */
        public static final Header CONTENT_TYPE_TEXT_PLAIN = createCached(HeaderNames.CONTENT_TYPE, "text/plain");
        /**
         * Content type octet stream.
         */
        public static final Header CONTENT_TYPE_OCTET_STREAM = createCached(HeaderNames.CONTENT_TYPE,
                                                                                        "application/octet-stream");
        /**
         * Content type SSE event stream.
         */
        public static final Header CONTENT_TYPE_EVENT_STREAM = createCached(HeaderNames.CONTENT_TYPE,
                                                                                        "text/event-stream");

        /**
         * Accept application/json.
         */
        public static final Header ACCEPT_JSON = createCached(HeaderNames.ACCEPT, "application/json");
        /**
         * Accept text/plain with UTF-8.
         */
        public static final Header ACCEPT_TEXT = createCached(HeaderNames.ACCEPT, "text/plain;charset=UTF-8");
        /**
         * Accept text/event-stream.
         */
        public static final Header ACCEPT_EVENT_STREAM = createCached(HeaderNames.ACCEPT, "text/event-stream");
        /**
         * Expect 100 header.
         */
        public static final Header EXPECT_100 = createCached(HeaderNames.EXPECT, "100-continue");
        /**
         * Content length with 0 value.
         */
        public static final Header CONTENT_LENGTH_ZERO = createCached(HeaderNames.CONTENT_LENGTH, "0");
        /**
         * Cache control without any caching.
         */
        public static final Header CACHE_NO_CACHE = create(HeaderNames.CACHE_CONTROL, "no-cache",
                                                                       "no-store",
                                                                       "must-revalidate",
                                                                       "no-transform");
        /**
         * Cache control that allows caching with no transform.
         */
        public static final Header CACHE_NORMAL = createCached(HeaderNames.CACHE_CONTROL, "no-transform");

        /**
         * TE header set to {@code trailers}, used to enable trailer headers.
         */
        public static final Header TE_TRAILERS = createCached(HeaderNames.TE, "trailers");

        private Headers() {
        }

        /**
         * Create and cache byte value.
         * Use this method if the header value is stored in a constant, or used repeatedly.
         *
         * @param name  header name
         * @param value value of the header
         * @return a new header
         */
        public static Header createCached(String name, String value) {
            return createCached(HeaderNames.create(name), value);
        }

        /**
         * Create and cache byte value.
         * Use this method if the header value is stored in a constant, or used repeatedly.
         *
         * @param name  header name
         * @param value value of the header
         * @return a new header
         */
        public static Header createCached(String name, int value) {
            return createCached(HeaderNames.create(name), value);
        }

        /**
         * Create and cache byte value.
         * Use this method if the header value is stored in a constant, or used repeatedly.
         *
         * @param name  header name
         * @param value value of the header
         * @return a new header
         */
        public static Header createCached(String name, long value) {
            return createCached(HeaderNames.create(name), value);
        }

        /**
         * Create and cache byte value.
         * Use this method if the header value is stored in a constant, or used repeatedly.
         *
         * @param name  header name
         * @param value value of the header
         * @return a new header
         */
        public static Header createCached(HeaderName name, String value) {
            return new HeaderValueCached(name, false,
                                         false,
                                         value.getBytes(StandardCharsets.US_ASCII),
                                         value);
        }

        /**
         * Create and cache byte value.
         * Use this method if the header value is stored in a constant, or used repeatedly.
         *
         * @param name  header name
         * @param value value of the header
         * @return a new header
         */
        public static Header createCached(HeaderName name, int value) {
            return createCached(name, String.valueOf(value));
        }

        /**
         * Create and cache byte value.
         * Use this method if the header value is stored in a constant, or used repeatedly.
         *
         * @param name  header name
         * @param value value of the header
         * @return a new header
         */
        public static Header createCached(HeaderName name, long value) {
            return createCached(name, String.valueOf(value));
        }

        /**
         * Create a new header with a single value. This header is considered unchanging and not sensitive.
         *
         * @param name  name of the header
         * @param value lazy string with the value
         * @return a new header
         * @see #create(HeaderName, boolean, boolean, String...)
         */
        public static Header create(HeaderName name, LazyString value) {
            Objects.requireNonNull(name);
            Objects.requireNonNull(value);

            return new HeaderValueLazy(name, false, false, value);
        }

        /**
         * Create a new header with a single value. This header is considered unchanging and not sensitive.
         *
         * @param name  name of the header
         * @param value integer value of the header
         * @return a new header
         * @see #create(HeaderName, boolean, boolean, String...)
         */
        public static Header create(HeaderName name, int value) {
            Objects.requireNonNull(name);

            return new HeaderValueSingle(name, false, false, String.valueOf(value));
        }

        /**
         * Create a new header with a single value. This header is considered unchanging and not sensitive.
         *
         * @param name  name of the header
         * @param value long value of the header
         * @return a new header
         * @see #create(HeaderName, boolean, boolean, String...)
         */
        public static Header create(HeaderName name, long value) {
            Objects.requireNonNull(name);

            return new HeaderValueSingle(name, false, false, String.valueOf(value));
        }

        /**
         * Create a new header with a single value. This header is considered unchanging and not sensitive.
         *
         * @param name  name of the header
         * @param value value of the header
         * @return a new header
         * @see #create(HeaderName, boolean, boolean, String...)
         */
        public static Header create(HeaderName name, String value) {
            Objects.requireNonNull(name, "HeaderName must not be null");
            Objects.requireNonNull(value, "HeaderValue must not be null");

            return new HeaderValueSingle(name,
                                         false,
                                         false,
                                         value);
        }

        /**
         * Create a new header with a single value. This header is considered unchanging and not sensitive.
         *
         * @param name  name of the header
         * @param value value of the header
         * @return a new header
         * @see #create(HeaderName, boolean, boolean, String...)
         */
        public static Header create(String name, String value) {
            Objects.requireNonNull(name, "Header name must not be null");

            return create(HeaderNames.create(name), value);
        }

        /**
         * Create a new header with a single value. This header is considered unchanging and not sensitive.
         *
         * @param name  name of the header
         * @param value value of the header
         * @return a new header
         * @see #create(HeaderName, boolean, boolean, String...)
         */
        public static Header create(String name, int value) {
            Objects.requireNonNull(name, "Header name must not be null");

            return create(HeaderNames.create(name), value);
        }

        /**
         * Create a new header with a single value. This header is considered unchanging and not sensitive.
         *
         * @param name  name of the header
         * @param value value of the header
         * @return a new header
         * @see #create(HeaderName, boolean, boolean, String...)
         */
        public static Header create(String name, long value) {
            Objects.requireNonNull(name, "Header name must not be null");

            return create(HeaderNames.create(name), value);
        }

        /**
         * Create a new header. This header is considered unchanging and not sensitive.
         *
         * @param name   name of the header
         * @param values values of the header
         * @return a new header
         * @see #create(HeaderName, boolean, boolean, String...)
         */
        public static Header create(HeaderName name, String... values) {
            if (values.length == 0) {
                throw new IllegalArgumentException("Cannot create a header without a value. Header: " + name);
            }
            return new HeaderValueArray(name, false, false, values);
        }

        /**
         * Create a new header. This header is considered unchanging and not sensitive.
         *
         * @param name   name of the header
         * @param values values of the header
         * @return a new header
         * @see #create(HeaderName, boolean, boolean, String...)
         */
        public static Header create(String name, String... values) {
            return create(HeaderNames.create(name), values);
        }

        /**
         * Create a new header. This header is considered unchanging and not sensitive.
         *
         * @param name   name of the header
         * @param values values of the header
         * @return a new header
         * @see #create(HeaderName, boolean, boolean, String...)
         */
        public static Header create(HeaderName name, Collection<String> values) {
            return new HeaderValueList(name, false, false, values);
        }

        /**
         * Create a new header. This header is considered unchanging and not sensitive.
         *
         * @param name   name of the header
         * @param values values of the header
         * @return a new header
         * @see #create(HeaderName, boolean, boolean, String...)
         */
        public static Header create(String name, Collection<String> values) {
            return create(HeaderNames.create(name), values);
        }

        /**
         * Create and cache byte value.
         * Use this method if the header value is stored in a constant, or used repeatedly.
         *
         * @param name      header name
         * @param changing  whether the value is changing often (to disable caching for HTTP/2)
         * @param sensitive whether the value is sensitive (to disable caching for HTTP/2)
         * @param value     value of the header
         * @return a new header
         */
        public static Header createCached(HeaderName name, boolean changing, boolean sensitive, String value) {
            return new HeaderValueCached(name, changing, sensitive, value.getBytes(StandardCharsets.UTF_8), value);
        }

        /**
         * Create a new header.
         *
         * @param name      name of the header
         * @param changing  whether the value is changing often (to disable caching for HTTP/2)
         * @param sensitive whether the value is sensitive (to disable caching for HTTP/2)
         * @param values    value(s) of the header
         * @return a new header
         */
        public static Header create(HeaderName name, boolean changing, boolean sensitive, String... values) {
            return new HeaderValueArray(name, changing, sensitive, values);
        }

        /**
         * Create a new header.
         *
         * @param name      name of the header
         * @param changing  whether the value is changing often (to disable caching for HTTP/2)
         * @param sensitive whether the value is sensitive (to disable caching for HTTP/2)
         * @param value     value of the header
         * @return a new header
         */
        public static Header create(HeaderName name, boolean changing, boolean sensitive, int value) {
            return create(name, changing, sensitive, String.valueOf(value));
        }

        /**
         * Create a new header.
         *
         * @param name      name of the header
         * @param changing  whether the value is changing often (to disable caching for HTTP/2)
         * @param sensitive whether the value is sensitive (to disable caching for HTTP/2)
         * @param value     value of the header
         * @return a new header
         */
        public static Header create(HeaderName name, boolean changing, boolean sensitive, long value) {
            return create(name, changing, sensitive, String.valueOf(value));
        }
    }

    /**
     * Support for HTTP date formats based on <a href="https://tools.ietf.org/html/rfc2616">RFC2616</a>.
     */
    public static final class DateTime {
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
}
