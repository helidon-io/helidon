/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
    private static final long CONNECTION_WORD_1 = 'C'
            | 'o' << 8
            | 'n' << 16
            | 'n' << 24
            | (long) 'e' << 32
            | (long) 'c' << 40
            | (long) 't' << 48
            | (long) 'i' << 56;
    private static final int CONNECTION_WORD_2 = 'o'
            | 'n' << 8;
    private static final long ACCEPT_WORD = 'A'
            | 'c' << 8
            | 'c' << 16
            | 'e' << 24
            | (long) 'p' << 32
            | (long) 't' << 40;

    private static final int HOST_WORD = 'H'
            | 'o' << 8
            | 's' << 16
            | 't' << 24;

    private static final long USER_AGENT_WORD_1 = 'U'
            | 's' << 8
            | 'e' << 16
            | 'r' << 24
            | (long) '-' << 32
            | (long) 'A' << 40
            | (long) 'g' << 48
            | (long) 'e' << 56;
    private static final int USER_AGENT_WORD_2 = 'n'
            | 't' << 8;

    private static final long CONTENT_WORD = 'C'
            | 'o' << 8
            | 'n' << 16
            | 't' << 24
            | (long) 'e' << 32
            | (long) 'n' << 40
            | (long) 't' << 48
            | (long) '-' << 56;

    private static final int TYPE_WORD = 'T'
            | 'y' << 8
            | 'p' << 16
            | 'e' << 24;

    private static final long LENGTH_WORD = 'L'
            | 'e' << 8
            | 'n' << 16
            | 'g' << 24
            | (long) 't' << 32
            | (long) 'h' << 40;

    private static final byte[] HEADERS = """
            Host: localhost:8080\r
            User-Agent: curl/7.68.0\r
            Connection: keep-alive\r
            Accept: text/plain,text/html;q=0.9,application/xhtml+xml;q=0.9,application/xml;q=0.8,*/*;q=0.7\r
            \r
            """.getBytes(StandardCharsets.US_ASCII);

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

            int eol = reader.findNewLine(maxLength);
            if (eol == maxLength) {
                throw new IllegalStateException("Header size exceeded");
            }
            byte[] headerBytes = reader.readBytes(eol);
            reader.skip(2); // skip CRLF
            maxLength -= eol + 1;
            if (maxLength < 0) {
                throw new IllegalStateException("Header size exceeded");
            }

            // now reader is on the start of the next header (or end of headers)
            // headerBytes contains something like: `Connection: keep-alive`
            int index = Bytes.firstIndexOf(headerBytes, 0, headerBytes.length, Bytes.COLON_BYTE);
            if (index < 0) {
                throw new IllegalArgumentException("Invalid header, missing colon:\n" + reader.debugDataHex());
            }

            HeaderName header = readHeaderName(headerBytes, index);
            // we do not need the string until somebody asks for this header (unless validation is on)
            LazyString value = new LazyString(headerBytes, index + 1, (headerBytes.length - index - 1), StandardCharsets.US_ASCII);

            Header headerValue = HeaderValues.create(header, value);
            headers.add(headerValue);
            if (validate) {
                headerValue.validate();
            }

        }
    }

    public static void main(String[] args) {
        long c = 0;
        for (int i = 0; i < 10000000; i++) {
            DataReader reader = new DataReader(() -> HEADERS);
            Http1HeadersParser p = new Http1HeadersParser();
            WritableHeaders<?> headers = p.readHeaders(reader, 1024, false);
            c += headers.size();
        }
        System.out.println("Combined: " + c);
    }

    private static HeaderName readHeaderName(byte[] bytes,
                                             int colonIndex) {

        // fast path for specific headers (most commonly used)
        if (colonIndex == 4) {
            if (isHost(bytes)) {
                return HeaderNames.HOST;
            }
        }
        if (colonIndex == 6) {
            if (isAccept(bytes)) {
                return HeaderNames.ACCEPT;
            }
        }
        long firstWord = longWord(bytes, 0);

        if (colonIndex == 10) {
            if (isConnection(bytes, firstWord)) {
                return HeaderNames.CONNECTION;
            }
            if (isUserAgent(bytes, firstWord)) {
                return HeaderNames.USER_AGENT;
            }
        }
        if (colonIndex == 12) {
            if (isContentType(bytes, firstWord)) {
                return HeaderNames.CONTENT_TYPE;
            }
        }
        if (colonIndex == 14) {
            if (isContentLength(bytes, firstWord)) {
                return HeaderNames.CONTENT_LENGTH;
            }
        }

        String headerName = new String(bytes, 0, colonIndex, StandardCharsets.US_ASCII);
        return HeaderNames.create(headerName);
    }

    private static boolean isHost(byte[] bytes) {
        int word = bytes[0]
                | bytes[1] << 8
                | bytes[2] << 16
                | bytes[3] << 24;
        return HOST_WORD == word;
    }

    private static boolean isAccept(byte[] buffer) {
        long word = buffer[0] |
                buffer[1] << 8 |
                buffer[2] << 16 |
                buffer[3] << 24 |
                (long) buffer[4] << 32 |
                (long) buffer[5] << 40;
        return word == ACCEPT_WORD;
    }

    private static boolean isUserAgent(byte[] buffer, long firstWord) {
        if (firstWord != USER_AGENT_WORD_1) {
            return false;
        }
        short endWord = (short) (buffer[8] | (short) buffer[9] << 8);
        return endWord == USER_AGENT_WORD_2;
    }


    private static long longWord(byte[] buffer, int offset) {
        return buffer[offset] |
                buffer[offset + 1] << 8 |
                buffer[offset + 2] << 16 |
                buffer[offset + 3] << 24 |
                (long) buffer[offset + 4] << 32 |
                (long) buffer[offset + 5] << 40 |
                (long) buffer[offset + 6] << 48 |
                (long) buffer[offset + 7] << 56;
    }

    private static boolean isContentLength(byte[] buffer, long firstWord) {
        if (firstWord != CONTENT_WORD) {
            return false;
        }
        long endWord = buffer[8]
                | buffer[9] << 8
                | buffer[10] << 16
                | buffer[11] << 24
                | (long) buffer[12] << 32
                | (long) buffer[13] << 40;

        return endWord == LENGTH_WORD;
    }

    private static boolean isContentType(byte[] buffer, long firstWord) {
        if (firstWord != CONTENT_WORD) {
            return false;
        }
        int endWord = buffer[8]
                | buffer[9] << 8
                | buffer[10] << 16
                | buffer[11] << 24;

        return endWord == TYPE_WORD;
    }

    private static boolean isConnection(byte[] buffer, long firstWord) {
        if (firstWord != CONNECTION_WORD_1) {
            return false;
        }
        short endWord = (short) (buffer[8] | (short) buffer[9] << 8);
        return endWord == CONNECTION_WORD_2;
    }
}
