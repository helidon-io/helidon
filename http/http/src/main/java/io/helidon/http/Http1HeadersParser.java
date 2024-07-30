/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import io.helidon.common.buffers.Bytes;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.LazyString;

/**
 * Used by both HTTP server and client to parse headers from {@link io.helidon.common.buffers.DataReader}.
 */
public final class Http1HeadersParser {
    private static final byte[] HD_HOST = (HeaderNameEnum.HOST.defaultCase() + ":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] HD_ACCEPT = (HeaderNameEnum.ACCEPT.defaultCase() + ":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] HD_CONNECTION =
            (HeaderNameEnum.CONNECTION.defaultCase() + ":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] HD_USER_AGENT =
            (HeaderNameEnum.USER_AGENT.defaultCase() + ":").getBytes(StandardCharsets.UTF_8);
    private static final long CLOSE_WORD = ' '
            | 'c' << 8
            | 'l' << 16
            | 'o' << 24
            | (long) 's' << 32
            | (long) 'e' << 40;
    private static final long KEEP_ALIVE_WORD_1 = ' '
            | 'k' << 8
            | 'e' << 16
            | 'e' << 24
            | (long) 'p' << 32
            | (long) '-' << 40
            | (long) 'a' << 48
            | (long) 'l' << 56;
    private static final long KEEP_ALIVE_WORD_2 = 'i'
            | 'v' << 8
            | 'e' << 16;

    private Http1HeadersParser() {
    }

    /**
     * Read headers from the provided reader.
     *
     * @param reader         reader to pull data from
     * @param maxHeadersSize maximal size of all headers, in bytes
     * @param validate       whether to validate headers
     * @return a new mutable headers instance containing all headers parsed from reader
     */
    public static WritableHeaders<?> readHeaders(DataReader reader, int maxHeadersSize, boolean validate) {
        WritableHeaders<?> headers = WritableHeaders.create();
        int maxLength = maxHeadersSize;

        while (true) {
            if (reader.startsWithNewLine()) { // new line found at 0
                reader.skip(2);
                return headers;
            }

            HeaderName header = readHeaderName(reader, maxLength);
            maxLength -= header.defaultCase().length() + 2;
            int eol = reader.findNewLine(maxLength);
            if (eol == maxLength) {
                throw new IllegalStateException("Header size exceeded");
            }
            // optimization for Connection, as we always need to analyze it
            Header headerValue;
            if (header.equals(HeaderNames.CONNECTION)) {
                headerValue = connectionHeaderValue(reader, eol);
            } else {
                // we do not need the string until somebody asks for this header (unless validation is on)
                LazyString value = reader.readLazyString(StandardCharsets.US_ASCII, eol);
                headerValue = HeaderValues.create(header, value);
            }
            reader.skip(2);
            maxLength -= eol + 1;

            headers.add(headerValue);
            if (validate) {
                headerValue.validate();
            }
            if (maxLength < 0) {
                throw new IllegalStateException("Header size exceeded");
            }
        }
    }

    private static Header connectionHeaderValue(DataReader reader, int eol) {
        byte[] bytes = reader.readBytes(eol);

        // space and `keep-alive`
        if (bytes.length == 11) {
            if (isKeepAlive(bytes)) {
                return HeaderValues.CONNECTION_KEEP_ALIVE;
            }
        }
        // space and `close`
        if (bytes.length == 6 && isClose(bytes)) {
            return HeaderValues.CONNECTION_CLOSE;
        }
        // some other (unexpected) combination
        return HeaderValues.create(HeaderNames.CONNECTION, new LazyString(bytes, 0, bytes.length, StandardCharsets.US_ASCII));
    }

    private static boolean isKeepAlive(byte[] buffer) {
        if (Bytes.toWord(buffer, 0) != KEEP_ALIVE_WORD_1) {
            return false;
        }
        long endWord = buffer[8] & 0xff
                | (buffer[9] & 0xff) << 8
                | (buffer[10] & 0xff) << 16;
        return endWord == KEEP_ALIVE_WORD_2;
    }

    private static boolean isClose(byte[] buffer) {
        long word = buffer[0] & 0xff
                | (buffer[1] & 0xff) << 8
                | (buffer[2] & 0xff) << 16
                | ((long) buffer[3] & 0xff) << 24
                | ((long) buffer[4]  & 0xff) << 32
                | ((long) buffer[5]  & 0xff) << 40;
        return word == CLOSE_WORD;
    }

    private static HeaderName readHeaderName(DataReader reader,
                                             int maxLength) {
        switch (reader.lookup()) {
        case (byte) 'H' -> {
            if (reader.startsWith(HD_HOST)) {
                reader.skip(HD_HOST.length);
                return HeaderNameEnum.HOST;
            }
        }
        case (byte) 'A' -> {
            if (reader.startsWith(HD_ACCEPT)) {
                reader.skip(HD_ACCEPT.length);
                return HeaderNameEnum.ACCEPT;
            }
        }
        case (byte) 'C' -> {
            if (reader.startsWith(HD_CONNECTION)) {
                reader.skip(HD_CONNECTION.length);
                return HeaderNameEnum.CONNECTION;
            }
        }
        case (byte) 'U' -> {
            if (reader.startsWith(HD_USER_AGENT)) {
                reader.skip(HD_USER_AGENT.length);
                return HeaderNameEnum.USER_AGENT;
            }
        }
        default -> {
        }
        }
        int col = reader.findOrNewLine(Bytes.COLON_BYTE, maxLength);
        if (col == maxLength) {
            throw new IllegalStateException("Header size exceeded");
        } else if (col < 0) {
            throw new IllegalArgumentException("Invalid header, missing colon:\n" + reader.debugDataHex());
        }

        String headerName = reader.readAsciiString(col);
        HeaderName header = HeaderNames.create(headerName);
        reader.skip(1); // skip the colon character

        return header;
    }
}
