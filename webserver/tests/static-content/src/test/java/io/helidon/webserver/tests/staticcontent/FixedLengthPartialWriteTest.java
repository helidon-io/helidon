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

package io.helidon.webserver.tests.staticcontent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.helidon.common.socket.SocketOptions;
import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.spi.ServerFeature;
import io.helidon.webserver.staticcontent.StaticContentFeature;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpFeatures;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for issue 11340.
 *
 * <p>The issue reported broken fixed-length HTTP/1.1 responses when the NIO socket implementation hit
 * partial writes. The reproducer covered both a handler that set {@code Content-Length} before streaming
 * through {@code outputStream()} and a {@code StaticContentFeature} file response, so this test exercises
 * both with a slow raw-socket client and verifies the full JSON body arrives intact.
 */
@ServerTest
class FixedLengthPartialWriteTest {
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
    private static final String FIXED_RESPONSE_JSON = responseJson("fixed-length");
    private static final String STATIC_RESPONSE_JSON = responseJson("static-content");
    private static final Path STATIC_FILE = createStaticFile();

    private final URI uri;

    FixedLengthPartialWriteTest(URI uri) {
        this.uri = uri;
    }

    @SetUpFeatures
    static List<ServerFeature> setupFeatures() {
        return List.of(StaticContentFeature.builder()
                               .addPath(path -> path.context("/static")
                                       .location(STATIC_FILE))
                               .build());
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
        rules.get("/fixed", FixedLengthPartialWriteTest::fixed);
    }

    @Test
    void fixedLengthResponseSurvivesPartialNioWrites() throws Exception {
        assertEquals(FIXED_RESPONSE_JSON, requestSlowly("/fixed"));
    }

    @Test
    void staticContentResponseSurvivesPartialNioWrites() throws Exception {
        assertEquals(STATIC_RESPONSE_JSON, requestSlowly("/static"));
    }

    private static void fixed(ServerRequest req, ServerResponse res) throws IOException {
        byte[] bytes = FIXED_RESPONSE_JSON.getBytes(StandardCharsets.UTF_8);
        res.contentLength(bytes.length);
        try (InputStream inputStream = new ByteArrayInputStream(bytes);
             OutputStream outputStream = res.outputStream()) {
            inputStream.transferTo(outputStream);
        }
    }

    private String requestSlowly(String path) throws Exception {
        try (SocketHttpClient socketHttpClient = socketHttpClient()) {
            socketHttpClient.requestRaw(httpRequest(uri, path));

            Thread.sleep(INITIAL_READ_DELAY_MILLIS);

            String rawResponse = readSlowly(socketHttpClient.socketInputStream());
            return decodeFixedLengthJson(rawResponse);
        }
    }

    private SocketHttpClient socketHttpClient() {
        return SocketHttpClient.create(uri.getHost(),
                                       uri.getPort(),
                                       Duration.ofMillis(SOCKET_TIMEOUT_MILLIS),
                                       FixedLengthPartialWriteTest::configureSocket);
    }

    private static void configureSocket(Socket socket) {
        try {
            socket.setReceiveBufferSize(READ_BUFFER_SIZE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to configure test client socket", e);
        }
    }

    private static String httpRequest(URI uri, String path) {
        return """
                GET %s HTTP/1.1\r
                Host: %s:%d\r
                Connection: close\r
                \r
                """.formatted(path, uri.getHost(), uri.getPort());
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
                decodeFixedLengthJson(partialResponse);
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

        Optional<Integer> contentLength = contentLength(rawResponse.substring(0, headersEnd));
        if (contentLength.isEmpty()) {
            return ResponseState.MALFORMED;
        }

        String body = rawResponse.substring(headersEnd + (CRLF + CRLF).length());
        int actualLength = body.getBytes(StandardCharsets.UTF_8).length;
        if (actualLength < contentLength.get()) {
            return ResponseState.INCOMPLETE;
        }
        if (actualLength == contentLength.get()) {
            return ResponseState.COMPLETE;
        }
        return ResponseState.MALFORMED;
    }

    private static String decodeFixedLengthJson(String rawResponse) {
        int headersEnd = rawResponse.indexOf(CRLF + CRLF);
        assertTrue(headersEnd >= 0, () -> "Missing response headers terminator:\n" + abbreviate(rawResponse));

        String headers = rawResponse.substring(0, headersEnd);
        assertTrue(headers.startsWith("HTTP/1.1 200"),
                   () -> "Unexpected status line:\n" + abbreviate(headers));
        assertFalse(headers.toLowerCase(Locale.ROOT).contains("transfer-encoding: chunked"),
                    () -> "Expected fixed-length response, but got chunked transfer-encoding:\n" + abbreviate(headers));

        int contentLength = contentLength(headers)
                .orElseThrow(() -> new AssertionError("Missing Content-Length header:\n" + abbreviate(headers)));

        String body = rawResponse.substring(headersEnd + (CRLF + CRLF).length());
        assertEquals(contentLength,
                     body.getBytes(StandardCharsets.UTF_8).length,
                     () -> "Expected Content-Length " + contentLength + " but received:\n" + abbreviate(rawResponse));
        return body;
    }

    private static Optional<Integer> contentLength(String headers) {
        return headers.lines()
                .skip(1)
                .map(String::trim)
                .filter(line -> line.toLowerCase(Locale.ROOT).startsWith("content-length:"))
                .findFirst()
                .map(line -> Integer.parseInt(line.substring("content-length:".length()).trim()));
    }

    private static String responseJson(String variant) {
        String items = IntStream.range(0, ITEM_COUNT)
                .mapToObj(i -> "    " + """
                        {"id":%d,"name":"item-%d","active":%s,"payload":"%s"}"""
                        .formatted(i, i, i % 2 == 0, ITEM_PAYLOAD))
                .collect(Collectors.joining(",\n"));

        return """
                {
                  "variant": "%s",
                  "items": [
                %s
                  ],
                  "meta": {
                    "count": %d,
                    "kind": "fixed-length"
                  }
                }
                """.formatted(variant, items, ITEM_COUNT);
    }

    private static Path createStaticFile() {
        try {
            Path file = Files.createTempFile("helidon-11340-", ".json");
            Files.writeString(file, STATIC_RESPONSE_JSON, StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to prepare static content for the test", e);
        }
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
