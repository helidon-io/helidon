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

package io.helidon.webclient.http1;

import java.nio.charset.StandardCharsets;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.Bytes;
import io.helidon.common.buffers.DataReader;
import io.helidon.http.Status;

/**
 * Parser of HTTP/1.0 or HTTP/1.1 response status.
 */
public final class Http1StatusParser {
    private Http1StatusParser() {
    }

    /**
     * Read the status line from HTTP/1.0 or HTTP/1.1 response.
     *
     * @param reader    data reader to obtain bytes from
     * @param maxLength maximal number of bytes that can be processed before end of line is reached
     * @return parsed HTTP status
     * @throws java.lang.IllegalStateException                                         in case of unexpected data
     * @throws io.helidon.common.buffers.DataReader.InsufficientDataAvailableException when not enough data can be obtained
     * @throws io.helidon.common.buffers.DataReader.IncorrectNewLineException          in case we are missing correct end of line
     *                                                                                 (CRLF)
     * @throws java.lang.RuntimeException                                              additional exceptions may be thrown from
     *                                                                                 the reader, depending on its
     *                                                                                 implementation
     */
    public static Status readStatus(DataReader reader, int maxLength) {
        int newLine = reader.findNewLine(maxLength);
        if (newLine == maxLength) {
            throw new IllegalStateException("HTTP Response did not contain HTTP status line. Line: \n"
                                                    + reader.readBuffer(newLine).debugDataHex());
        }
        int slash = reader.findOrNewLine(Bytes.SLASH_BYTE, newLine);
        if (slash == newLine) {
            throw new IllegalStateException("HTTP Response did not contain HTTP status line. Line: \n"
                                                    + reader.readBuffer(newLine).debugDataHex());
        }
        String protocol = reader.readAsciiString(slash);
        if (!protocol.equals("HTTP")) {
            throw new IllegalStateException("HTTP response did not contain correct status line. Protocol is not HTTP: \n"
                                                    + BufferData.create(protocol.getBytes(StandardCharsets.US_ASCII))
                    .debugDataHex());
        }
        reader.skip(1); // /
        newLine -= slash;
        newLine--;
        int space = reader.findOrNewLine(Bytes.SPACE_BYTE, newLine);
        if (space == newLine) {
            throw new IllegalStateException("HTTP Response did not contain HTTP status line. Line: HTTP/\n"
                                                    + reader.readBuffer(newLine).debugDataHex());
        }
        String protocolVersion = reader.readAsciiString(space);
        reader.skip(1); // space
        newLine -= space;
        newLine--;
        if (!protocolVersion.equals("1.0") && !protocolVersion.equals("1.1")) {
            throw new IllegalStateException("HTTP response did not contain correct status line. Version is not 1.0 or 1.1: \n"
                                                    + BufferData.create(protocolVersion.getBytes(StandardCharsets.US_ASCII))
                    .debugDataHex());
        }
        // HTTP/1.0 or HTTP/1.1 200 OK
        space = reader.findOrNewLine(Bytes.SPACE_BYTE, newLine);
        if (space == newLine) {
            throw new IllegalStateException("HTTP Response did not contain HTTP status line. Line: HTTP/1.0 or HTTP/1.1\n"
                                                    + reader.readBuffer(newLine).debugDataHex());
        }
        String code = reader.readAsciiString(space);
        reader.skip(1); // the new line
        newLine -= space;
        newLine--;
        String phrase = reader.readAsciiString(newLine); // the rest of the line is reason phrase
        reader.skip(2); // skip the last CRLF

        try {
            return Status.create(Integer.parseInt(code), phrase);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("HTTP Response did not contain HTTP status line. Line HTTP/1.0 or HTTP/1.1 \n"
                                                    + BufferData.create(code.getBytes(StandardCharsets.US_ASCII)) + "\n"
                                                    + BufferData.create(phrase.getBytes(StandardCharsets.US_ASCII)));
        }
    }
}
