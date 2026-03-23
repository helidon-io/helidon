/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.helidon.common.socket.SocketOptions;
import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for issue 11353.
 *
 * <p>This reproduces the chunked streaming corruption reported there by forcing the webserver through the
 * NIO socket path, using a small socket send buffer, and reading the response slowly enough to trigger
 * partial {@code SocketChannel.write(...)} calls. Before the fix, those partial writes could drop bytes,
 * corrupt the chunk framing, and leave the client with an incomplete JSON response.
 */
@ServerTest
public class ChunkedStreamingPartialWriteTest {
    private enum ResponseState {
        INCOMPLETE,
        COMPLETE,
        MALFORMED
    }

    private static final String CRLF = "\r\n";
    private static final int ITEM_COUNT = 600;
    private static final int INITIAL_READ_DELAY_MILLIS = 100;
    private static final int READ_DELAY_MILLIS = 1;
    private static final int READ_BUFFER_SIZE = 256;
    private static final int SOCKET_TIMEOUT_MILLIS = 2_000;
    private static final String ITEM_PAYLOAD = "x".repeat(192);
    private static final String REQUEST_JSON = """
            {"requestId":"chunked-streaming","payload":"%s"}
            """.formatted("r".repeat(1024));

    private final URI uri;

    ChunkedStreamingPartialWriteTest(URI uri) {
        this.uri = uri;
    }

    @SetUpServer
    static void setupServer(WebServerConfig.Builder builder) {
        builder.useNio(true);
        builder.writeBufferSize(0);
        builder.connectionOptions(SocketOptions.builder()
                                          .socketSendBufferSize(1024)
                                          .build());
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.post("/chunked-streaming", ChunkedStreamingPartialWriteTest::streamJsonResponse);
    }

    @Test
    void chunkedStreamingResponseSurvivesPartialNioWrites() throws Exception {
        String expectedJson = responseJson(REQUEST_JSON);
        String actualJson = requestSlowly(REQUEST_JSON);
        assertEquals(expectedJson, actualJson);
    }

    private static void streamJsonResponse(ServerRequest req, ServerResponse res) throws IOException {
        String requestJson = req.content().as(String.class);
        String responseJson = responseJson(requestJson);
        int firstSplit = responseJson.length() / 3;
        int secondSplit = (responseJson.length() * 2) / 3;
        String firstChunk = responseJson.substring(0, firstSplit);
        String secondChunk = responseJson.substring(firstSplit, secondSplit);
        String thirdChunk = responseJson.substring(secondSplit);

        try (OutputStream outputStream = res.outputStream()) {
            outputStream.write(firstChunk.getBytes(StandardCharsets.UTF_8));
            outputStream.write(secondChunk.getBytes(StandardCharsets.UTF_8));
            outputStream.write(thirdChunk.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String requestSlowly(String requestJson) throws Exception {
        try (SocketHttpClient socketHttpClient = socketHttpClient()) {
            socketHttpClient.requestRaw(httpRequest(uri, requestJson));

            Thread.sleep(INITIAL_READ_DELAY_MILLIS);

            String rawResponse = readSlowly(socketHttpClient.socketInputStream());
            return decodeChunkedJson(rawResponse);
        }
    }

    private SocketHttpClient socketHttpClient() {
        return SocketHttpClient.create(uri.getHost(),
                                       uri.getPort(),
                                       Duration.ofMillis(SOCKET_TIMEOUT_MILLIS),
                                       ChunkedStreamingPartialWriteTest::configureSocket);
    }

    private static void configureSocket(Socket socket) {
        try {
            socket.setReceiveBufferSize(READ_BUFFER_SIZE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to configure test client socket", e);
        }
    }

    private static String httpRequest(URI uri, String requestJson) {
        int contentLength = requestJson.getBytes(StandardCharsets.UTF_8).length;
        return """
                POST /chunked-streaming HTTP/1.1\r
                Host: %s:%d\r
                Content-Type: application/json\r
                Content-Length: %d\r
                Connection: close\r
                \r
                %s""".formatted(uri.getHost(), uri.getPort(), contentLength, requestJson);
    }

    private static String readSlowly(InputStream inputStream) throws IOException, InterruptedException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[READ_BUFFER_SIZE];

        try {
            while (true) {
                int read = inputStream.read(buffer);
                if (read == -1) {
                    return out.toString(StandardCharsets.UTF_8);
                }
                out.write(buffer, 0, read);
                String currentResponse = out.toString(StandardCharsets.UTF_8);
                ResponseState responseState = responseState(currentResponse);
                if (responseState != ResponseState.INCOMPLETE) {
                    return currentResponse;
                }
                Thread.sleep(READ_DELAY_MILLIS);
            }
        } catch (SocketTimeoutException e) {
            String partialResponse = out.toString(StandardCharsets.UTF_8);
            try {
                decodeChunkedJson(partialResponse);
            } catch (AssertionError assertionError) {
                throw assertionError;
            }
            throw new AssertionError("Timed out while reading response:\n" + abbreviate(partialResponse), e);
        }
    }

    private static ResponseState responseState(String rawResponse) {
        int headersEnd = rawResponse.indexOf(CRLF + CRLF);
        if (headersEnd < 0) {
            return ResponseState.INCOMPLETE;
        }

        String headers = rawResponse.substring(0, headersEnd).toLowerCase(Locale.ROOT);
        if (!headers.contains("transfer-encoding: chunked")) {
            return ResponseState.INCOMPLETE;
        }

        String chunkedBody = rawResponse.substring(headersEnd + (CRLF + CRLF).length());
        int index = 0;

        while (true) {
            int sizeLineEnd = chunkedBody.indexOf(CRLF, index);
            if (sizeLineEnd < 0) {
                return ResponseState.INCOMPLETE;
            }

            String chunkSizeLine = chunkedBody.substring(index, sizeLineEnd);
            String chunkSizeToken = chunkSizeLine.contains(";")
                    ? chunkSizeLine.substring(0, chunkSizeLine.indexOf(';'))
                    : chunkSizeLine;

            final int chunkSize;
            try {
                chunkSize = Integer.parseInt(chunkSizeToken, 16);
            } catch (NumberFormatException e) {
                return ResponseState.MALFORMED;
            }

            index = sizeLineEnd + CRLF.length();
            if (chunkSize == 0) {
                return chunkedBody.startsWith(CRLF, index) ? ResponseState.COMPLETE : ResponseState.INCOMPLETE;
            }

            int chunkEnd = index + chunkSize;
            if (chunkEnd > chunkedBody.length()) {
                return ResponseState.INCOMPLETE;
            }
            if (!chunkedBody.startsWith(CRLF, chunkEnd)) {
                return ResponseState.INCOMPLETE;
            }

            index = chunkEnd + CRLF.length();
        }
    }

    private static String decodeChunkedJson(String rawResponse) {
        int headersEnd = rawResponse.indexOf(CRLF + CRLF);
        assertTrue(headersEnd >= 0, () -> "Missing response headers terminator:\n" + abbreviate(rawResponse));

        String headers = rawResponse.substring(0, headersEnd);
        assertTrue(headers.startsWith("HTTP/1.1 200"),
                   () -> "Unexpected status line:\n" + abbreviate(headers));
        assertTrue(headers.toLowerCase(Locale.ROOT).contains("transfer-encoding: chunked"),
                   () -> "Expected chunked transfer-encoding:\n" + abbreviate(headers));

        return decodeChunkedBody(rawResponse.substring(headersEnd + (CRLF + CRLF).length()), rawResponse);
    }

    private static String decodeChunkedBody(String chunkedBody, String rawResponse) {
        StringBuilder decoded = new StringBuilder(chunkedBody.length());
        int index = 0;

        while (true) {
            int sizeLineEnd = chunkedBody.indexOf(CRLF, index);
            assertTrue(sizeLineEnd >= 0,
                       () -> "Missing chunk-size delimiter:\n" + abbreviate(rawResponse));

            String chunkSizeLine = chunkedBody.substring(index, sizeLineEnd);
            String chunkSizeToken = chunkSizeLine.contains(";")
                    ? chunkSizeLine.substring(0, chunkSizeLine.indexOf(';'))
                    : chunkSizeLine;

            final int chunkSize;
            try {
                chunkSize = Integer.parseInt(chunkSizeToken, 16);
            } catch (NumberFormatException e) {
                throw new AssertionError("Invalid chunk size '" + chunkSizeLine + "' in response:\n"
                                                 + abbreviate(rawResponse),
                                         e);
            }

            index = sizeLineEnd + CRLF.length();

            if (chunkSize == 0) {
                assertTrue(chunkedBody.startsWith(CRLF, index),
                           () -> "Missing terminating CRLF after the final chunk:\n" + abbreviate(rawResponse));
                return decoded.toString();
            }

            int chunkEnd = index + chunkSize;
            assertTrue(chunkEnd <= chunkedBody.length(),
                       () -> "Chunk size " + chunkSize + " exceeds remaining response bytes:\n"
                               + abbreviate(rawResponse));

            decoded.append(chunkedBody, index, chunkEnd);

            assertTrue(chunkedBody.startsWith(CRLF, chunkEnd),
                       () -> "Missing CRLF after chunk payload:\n" + abbreviate(rawResponse));

            index = chunkEnd + CRLF.length();
        }
    }

    private static String responseJson(String requestJson) {
        String items = IntStream.range(0, ITEM_COUNT)
                .mapToObj(i -> "    " + """
                        {"id":%d,"name":"item-%d","active":%s,"payload":"%s"}"""
                        .formatted(i, i, i % 2 == 0, ITEM_PAYLOAD))
                .collect(Collectors.joining(",\n"));

        return """
                {
                  "request": %s,
                  "items": [
                %s
                  ],
                  "meta": {
                    "count": %d,
                    "kind": "chunked-streaming"
                  }
                }
                """.formatted(requestJson, items, ITEM_COUNT);
    }

    private static String abbreviate(String text) {
        int maxLength = 4000;
        if (text.length() <= maxLength) {
            return text;
        }
        int tailLength = 1000;
        int headLength = maxLength - tailLength - 25;
        return text.substring(0, headLength)
                + "\n... truncated ...\n"
                + text.substring(text.length() - tailLength);
    }
}
