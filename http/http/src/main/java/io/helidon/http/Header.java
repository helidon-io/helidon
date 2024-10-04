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
import java.util.List;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.mapper.Value;

/**
 * HTTP Header with {@link io.helidon.http.HeaderName} and value.
 *
 * @see io.helidon.http.HeaderValues
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
                }
                return List.of(value);
            }
            return values;
        }
        return allValues();
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
        if (vLength >= 1) {
            char vChar = vChars[0];
            if (vChar < '!' || vChar == '\u007f') {
                throw new IllegalArgumentException("First character of the header value is invalid"
                        + " for header '" + name + "'");
            }
            for (int i = 1; i < vLength; i++) {
                vChar = vChars[i];
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
