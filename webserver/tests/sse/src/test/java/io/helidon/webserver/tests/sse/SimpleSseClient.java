/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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
package io.helidon.webserver.tests.sse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.http.Method;

class SimpleSseClient implements AutoCloseable {

    public enum State {
        DISCONNECTED,
        CONNECTED,
        HEADERS_READ,
        ERROR
    }

    private State state = State.DISCONNECTED;
    private final String path;
    private final SocketHttpClient client;
    private String contentEncoding;
    private InputStream inputStream;
    private Iterable<String> headers;

    public static SimpleSseClient create(int port, String path) {
        return create("localhost", port, path, Duration.ofSeconds(10));
    }

    public static SimpleSseClient create(int port, String path, Duration timeout) {
        return create("localhost", port, path, timeout);
    }

    public static SimpleSseClient create(String host, int port, String path, Duration timeout) {
        return new SimpleSseClient("localhost", port, path, List.of(), timeout);
    }

    public static SimpleSseClient create(String host, int port, String path, Iterable<String> headers, Duration timeout) {
        return new SimpleSseClient("localhost", port, path, headers, timeout);
    }

    private SimpleSseClient(String host, int port, String path, Iterable<String> headers, Duration timeout) {
        this.path = path;
        this.headers = headers;
        this.client = SocketHttpClient.create(host, port, timeout);
    }

    public String nextEvent() {
        ensureConnected();
        ensureHeadersRead();

        try {
            String line;
            StringBuilder event = new StringBuilder();
            while ((line = readLine(inputStream)) != null) {
                if (line.isEmpty()) {
                    return event.toString();
                }
                if (!event.isEmpty()) {
                    event.append("\n");
                }
                event.append(line);
            }
            if (event.isEmpty()) {
                return null;
            }
            state = State.ERROR;
            throw new RuntimeException("Unable to parse response");
        } catch (IOException e) {
            state = State.ERROR;
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        client.close();
        state = State.DISCONNECTED;

    }

    public State state() {
        return state;
    }

    private void ensureConnected() {
        if (state == State.DISCONNECTED) {
            client.request(Method.GET.toString(),
                           path,
                           "HTTP/1.1",
                           "localhost",
                           headers,
                           null);
            inputStream = client.socketInputStream();
            state = State.CONNECTED;
        }
    }

    private void ensureHeadersRead() {
        if (state == State.CONNECTED) {
            try {
                String line;
                while ((line = readLine(inputStream)) != null) {
                    if (line.isEmpty()) {
                        state = State.HEADERS_READ;
                        if (contentEncoding != null) {
                            if ("gzip".equals(contentEncoding)) {
                                inputStream = new GZIPInputStream(inputStream);
                            } else if ("deflate".equals(contentEncoding)) {
                                inputStream = new InflaterInputStream(inputStream);
                            } else {
                                throw new UnsupportedOperationException("Unsupported content encoding in response");
                            }
                        }
                        return;
                    }
                    line = line.toLowerCase();
                    if (line.contains("http/1.1") && !line.contains("200")) {
                        throw new RuntimeException("Invalid status code in response");
                    } else if (line.contains("content-type") && !line.contains("text/event-stream")) {
                        throw new RuntimeException("Invalid content-type in response");
                    } else if (line.contains("content-encoding")) {
                        contentEncoding = line.substring("content-encoding:".length()).trim();
                    }
                }
                state = State.ERROR;
                throw new RuntimeException("Unable to parse response");
            } catch (IOException e) {
                state = State.ERROR;
                throw new RuntimeException(e);
            }
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                buffer.write(b);
            }
        }
        if (b == -1 && buffer.size() == 0) {
            return null;
        }
        return buffer.toString("UTF-8");
    }
}
