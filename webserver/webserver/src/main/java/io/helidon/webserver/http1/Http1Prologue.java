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

package io.helidon.webserver.http1;

import java.nio.charset.StandardCharsets;

import io.helidon.common.buffers.Bytes;
import io.helidon.common.buffers.DataReader;
import io.helidon.http.DirectHandler;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.RequestException;
import io.helidon.http.Status;
import io.helidon.webserver.CloseConnectionException;
import io.helidon.webserver.http.DirectTransportRequest;

/**
 * HTTP 1 prologue parsing support.
 */
public final class Http1Prologue {
    private static final byte[] GET_BYTES = "GET ".getBytes(StandardCharsets.UTF_8); // space included

    private final DataReader reader;
    private final int maxLength;
    private final boolean validatePath;

    /**
     * Create a new prologue parser.
     *
     * @param reader       data reader
     * @param maxLength    maximal prologue length
     * @param validatePath whether to validate path
     */
    public Http1Prologue(DataReader reader, int maxLength, boolean validatePath) {
        this.reader = reader;
        this.maxLength = maxLength;
        this.validatePath = validatePath;
    }

    /**
     * Read next prologue.
     *
     * @return HTTP prologue
     */
    public HttpPrologue readPrologue() {
        try {
            return doRead();
        } catch (DataReader.InsufficientDataAvailableException e) {
            throw new CloseConnectionException("No more data available", e);
        }
    }

    private static RequestException badRequest(String message, String method, String path, String protocol, String version) {
        String protocolAndVersion;
        if (protocol.isBlank() && version.isBlank()) {
            protocolAndVersion = "";
        } else {
            protocolAndVersion = protocol + "/" + version;
        }
        return RequestException.builder()
                .type(DirectHandler.EventType.BAD_REQUEST)
                .request(DirectTransportRequest.create(protocolAndVersion, method, path))
                .message(message)
                .safeMessage(false)
                .build();
    }

    private static Method readMethod(DataReader reader, int maxLen) {
        if (reader.startsWith(GET_BYTES)) {
            reader.skip(GET_BYTES.length);
            return Method.GET;
        }
        int firstSpace = reader.findOrNewLine(Bytes.SPACE_BYTE, maxLen);
        if (firstSpace < 0) {
            throw badRequest("Invalid prologue, missing space " + reader.debugDataHex(), "", "", "", "");
        } else if (firstSpace == maxLen) {
            throw badRequest("Prologue size exceeded", "", "", "", "");
        }
        Method method = Method.create(reader.readAsciiString(firstSpace));
        reader.skip(1);
        return method;
    }

    private HttpPrologue doRead() {
        //   > GET /loom/slow HTTP/1.1
        String protocol;
        String path;
        Method method;
        try {
            int maxLen = maxLength;
            try {
                method = readMethod(reader, maxLen);
            } catch (IllegalArgumentException e) {
                // we need to validate method contains only allowed characters
                throw badRequest("Invalid prologue, method not valid (" + e.getMessage() + ")", "", "", "", "");
            }
            maxLen -= method.length(); // length of method
            maxLen--; // space

            int secondSpace = reader.findOrNewLine(Bytes.SPACE_BYTE, maxLen);
            if (secondSpace < 0) {
                throw badRequest("Invalid prologue" + reader.debugDataHex(), method.text(), "", "", "");
            } else if (secondSpace == maxLen) {
                throw RequestException.builder()
                        .message("Request URI too long.")
                        .type(DirectHandler.EventType.BAD_REQUEST)
                        .status(Status.REQUEST_URI_TOO_LONG_414)
                        .request(DirectTransportRequest.create("", method.text(), reader.readAsciiString(secondSpace)))
                        .build();
            }
            path = reader.readAsciiString(secondSpace);
            reader.skip(1);
            maxLen -= secondSpace;
            maxLen--; // space

            int eol = reader.findNewLine(maxLen);
            if (eol == maxLen) {
                throw badRequest("Prologue size exceeded", method.text(), path, "", "");
            }
            if (path.isBlank()) {
                throw badRequest("Path can't be empty", method.text(), path, "", "");
            }
            protocol = reader.readAsciiString(eol);
            reader.skip(2); // \r\n
        } catch (DataReader.IncorrectNewLineException e) {
            throw RequestException.builder()
                    .message("Invalid prologue: " + e.getMessage())
                    .type(DirectHandler.EventType.BAD_REQUEST)
                    .cause(e)
                    .build();
        }

        if (!"HTTP/1.1".equals(protocol)) {
            throw badRequest("Invalid protocol and/or version", method.text(), path, protocol, "");
        }

        try {
            return HttpPrologue.create(protocol,
                                       "HTTP",
                                       "1.1",
                                       method,
                                       path,
                                       validatePath);
        } catch (IllegalArgumentException e) {
            throw badRequest("Invalid path: " + e.getMessage(), method.text(), path, "HTTP", "1.1");
        }
    }
}
