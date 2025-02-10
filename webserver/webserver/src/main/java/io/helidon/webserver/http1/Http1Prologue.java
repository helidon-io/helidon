/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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
    /*
    The string HTTP/1.1 (as used in protocol version in prologue's third section)
     */
    private static final long HTTP_1_1_LONG = 'H'
            | 'T' << 8
            | 'T' << 16
            | 'P' << 24
            | (long) '/' << 32
            | (long) '1' << 40
            | (long) '.' << 48
            | (long) '1' << 56;
    /*
    The string HTTP/1.0: we don't support 1.0, but we detect it
    */
    private static final long HTTP_1_0_LONG = 'H'
            | 'T' << 8
            | 'T' << 16
            | 'P' << 24
            | (long) '/' << 32
            | (long) '1' << 40
            | (long) '.' << 48
            | (long) '0' << 56;
    /*
    GET string as int
     */
    private static final int GET_INT = 'G'
            | 'E' << 8
            | 'T' << 16;
    /*
    PUT string as int
    */
    private static final int PUT_INT = 'P'
            | 'U' << 8
            | 'T' << 16;
    /*
    POST string as int
     */
    private static final int POST_INT = 'P'
            | 'O' << 8
            | 'S' << 16
            | 'T' << 24;
    private static final String HTTP_1_1 = "HTTP/1.1";
    private static final String HTTP_1_0 = "HTTP/1.0";

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

    private static Method readMethod(byte[] bytes, int index, int spaceIndex) {
        int len = spaceIndex - index;
        if (len == 3) {
            if (isGetMethod(bytes, index)) {
                return Method.GET;
            }
            if (isPutMethod(bytes, index)) {
                return Method.PUT;
            }
        } else if (len == 4 && isPostMethod(bytes, index)) {
            return Method.POST;
        }
        return Method.create(new String(bytes, index, len, StandardCharsets.US_ASCII));
    }

    private static boolean isGetMethod(byte[] bytes, int index) {
        int maybeGet = bytes[index] & 0xff
                | (bytes[index + 1] & 0xff) << 8
                | (bytes[index + 2] & 0xff) << 16;
        return maybeGet == GET_INT;
    }

    private static boolean isPutMethod(byte[] bytes, int index) {
        int maybeGet = bytes[index] & 0xff
                | (bytes[index + 1] & 0xff) << 8
                | (bytes[index + 2] & 0xff) << 16;
        return maybeGet == PUT_INT;
    }

    private static boolean isPostMethod(byte[] bytes, int index) {
        int maybePost = bytes[index] & 0xff
                | (bytes[index + 1] & 0xff) << 8
                | (bytes[index + 2] & 0xff) << 16
                | (bytes[index + 3] & 0xff) << 24;
        return maybePost == POST_INT;
    }

    private HttpPrologue doRead() {
        int eol;

        try {
            eol = reader.findNewLine(maxLength);
        } catch (DataReader.IncorrectNewLineException e) {
            throw RequestException.builder()
                    .message("Invalid prologue: " + e.getMessage())
                    .type(DirectHandler.EventType.BAD_REQUEST)
                    .cause(e)
                    .build();
        }
        if (eol == maxLength) {
            // exceeded maximal length, we do not want to parse it anyway
            throw RequestException.builder()
                    .message("Request URI too long.")
                    .type(DirectHandler.EventType.BAD_REQUEST)
                    .status(Status.REQUEST_URI_TOO_LONG_414)
                    .build();
        }

        byte[] prologueBytes = reader.readBytes(eol);
        reader.skip(2); // \r\n

        //   > GET /loom/slow HTTP/1.1
        Method method;
        String path;
        String protocol;

        /*
        Read HTTP Method
         */
        int currentIndex = 0;
        int nextSpace = nextSpace(prologueBytes, currentIndex);
        if (nextSpace == -1) {
            throw badRequest("Invalid prologue, missing space " + reader.debugDataHex(), "", "", "", "");
        }
        method = readMethod(prologueBytes, currentIndex, nextSpace);
        currentIndex = nextSpace + 1; // continue after the space

        /*
        Read HTTP Path
        */
        nextSpace = nextSpace(prologueBytes, currentIndex);
        if (nextSpace == -1) {
            throw badRequest("Invalid prologue, missing space " + reader.debugDataHex(), method.text(), "", "", "");
        }
        path = new String(prologueBytes, currentIndex, (nextSpace - currentIndex), StandardCharsets.US_ASCII);
        currentIndex = nextSpace + 1; // continue after the space
        if (path.isBlank()) {
            throw badRequest("Path can't be empty", method.text(), path, "", "");
        }

        /*
        Read HTTP Version (we only support HTTP/1.1
         */
        protocol = readProtocol(prologueBytes, currentIndex);
        // we always use the same constant
        //noinspection StringEquality
        if (protocol != HTTP_1_1) {
            //noinspection StringEquality
            if (protocol == HTTP_1_0) {        // be friendly rejecting 1.0
                throw RequestException.builder()
                        .type(DirectHandler.EventType.HTTP_VERSION_NOT_SUPPORTED)
                        .request(DirectTransportRequest.create(HTTP_1_0, method.text(), path))
                        .message("HTTP 1.0 is not supported, consider using HTTP 1.1")
                        .safeMessage(true)
                        .build();
            }
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

    private int nextSpace(byte[] prologueBytes, int currentIndex) {
        return Bytes.firstIndexOf(prologueBytes, currentIndex, prologueBytes.length - 1, Bytes.SPACE_BYTE);
    }

    private String readProtocol(byte[] bytes, int index) {
        int length = bytes.length - index;

        if (length == 8) {
            long word = Bytes.toWord(bytes, index);
            if (word == HTTP_1_1_LONG) {
                return HTTP_1_1;
            } else if (word == HTTP_1_0_LONG) {
                return HTTP_1_0;
            }
        }

        return new String(bytes, StandardCharsets.US_ASCII);
    }
}
