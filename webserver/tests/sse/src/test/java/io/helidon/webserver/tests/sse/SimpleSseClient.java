/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;

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
    private BufferedReader reader;
    private final String path;
    private final SocketHttpClient client;

    public static SimpleSseClient create(int port, String path) {
        return create("localhost", port, path, Duration.ofSeconds(10));
    }

    public static SimpleSseClient create(int port, String path, Duration timeout) {
        return create("localhost", port, path, timeout);
    }

    public static SimpleSseClient create(String host, int port, String path, Duration timeout) {
        return new SimpleSseClient("localhost", port, path, timeout);
    }

    private SimpleSseClient(String host, int port, String path, Duration timeout) {
        this.path = path;
        this.client = SocketHttpClient.create(host, port, timeout);
    }

    public String nextEvent() {
        ensureConnected();
        ensureHeadersRead();

        try {
            String line;
            StringBuilder event = new StringBuilder();
            while ((line = reader.readLine()) != null) {
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
                           Collections.emptyList(),
                           null);
            reader = client.socketReader();
            state = State.CONNECTED;
        }
    }

    private void ensureHeadersRead() {
        if (state == State.CONNECTED) {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        state = State.HEADERS_READ;
                        return;
                    }
                    line = line.toLowerCase();
                    if (line.contains("http/1.1") && !line.contains("200")) {
                        throw new RuntimeException("Invalid status code in response");
                    } else if (line.contains("content-type") && !line.contains("text/event-stream")) {
                        throw new RuntimeException("Invalid content-type in response");
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
}
