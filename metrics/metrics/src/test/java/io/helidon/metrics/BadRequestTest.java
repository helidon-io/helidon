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
package io.helidon.metrics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

import io.helidon.common.http.Http;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import io.helidon.media.jsonp.JsonpSupport;

import jakarta.json.JsonObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

/**
 * Tests bad types in Accept header. Cannot be placed in webserver module due to
 * cyclic dependencies. It needs to use JSON and Metrics, and requires a "plain"
 * HTTP client to issue the request.
 */
public class BadRequestTest {

    private static WebServer server;

    @BeforeAll
    static void createAndStartServer() {
        server = WebServer.builder()
                .addMediaSupport(JsonpSupport.create())
                .addRouting(Routing.builder()
                        .register(MetricsSupport.create())
                        .post("/echo", Handler.create(JsonObject.class,
                                (req, res, entity) -> res.status(Http.Status.OK_200).send(entity))))
                .build();
        server.start().await();
    }

    @AfterAll
    static void stopServer() {
        server.shutdown().await();
    }

    @Test
    void testBadAcceptType() throws Exception {
        try (SimpleHttpSocketClient c = new SimpleHttpSocketClient(server)) {
            c.request("POST", "/echo", List.of("Accept: application.json"), "{}");
            String result = c.receive();
            assertThat(result, containsString("400 Bad Request"));
        }
    }

    @Test
    void testBadAcceptTypeMetrics() throws Exception {
        try (SimpleHttpSocketClient c = new SimpleHttpSocketClient(server)) {
            c.request("GET", "/metrics", List.of("Accept: application.json"), "");
            String result = c.receive();
            assertThat(result, containsString("400 Bad Request"));
        }
    }

    /**
     * Simple HTTP socket client to submit HTTP requests. Used by this class to create
     * a request with an invalid Accept header.
     */
    static class SimpleHttpSocketClient implements AutoCloseable {
        static final String EOL = "\r\n";
        private final Socket socket;
        private final BufferedReader socketReader;

        SimpleHttpSocketClient(WebServer webServer) throws IOException {
            socket = new Socket("localhost", webServer.port());
            socket.setSoTimeout(10000);
            socketReader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        }

        String receive() throws IOException {
            StringBuilder sb = new StringBuilder();
            String t;
            boolean ending = false;
            int contentLength = -1;
            while ((t = socketReader.readLine()) != null) {
                if (t.toLowerCase().startsWith("content-length")) {
                    int k = t.indexOf(':');
                    contentLength = Integer.parseInt(t.substring(k + 1).trim());
                }
                sb.append(t).append("\n");
                if ("".equalsIgnoreCase(t) && contentLength >= 0) {
                    char[] content = new char[contentLength];
                    socketReader.read(content);
                    sb.append(content);
                    break;
                }
                if (ending && "".equalsIgnoreCase(t)) {
                    break;
                }
                if (!ending && ("0".equalsIgnoreCase(t))) {
                    ending = true;
                }
            }
            return sb.toString();
        }

        void request(String method, String path, Iterable<String> headers, String payload)
                throws IOException {
            List<String> usedHeaders = new LinkedList<>();
            if (headers != null) {
                headers.forEach(usedHeaders::add);
            }
            usedHeaders.add(0, "Host: " + "localhost");
            PrintWriter pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            pw.print(method);
            pw.print(" ");
            pw.print(path);
            pw.print(" ");
            pw.print("http");
            pw.print(EOL);
            for (String header : usedHeaders) {
                pw.print(header);
                pw.print(EOL);
            }
            sendPayload(pw, payload);
            pw.print(EOL);
            pw.print(EOL);
            pw.flush();
        }

        void sendPayload(PrintWriter pw, String payload) {
            if (payload != null) {
                pw.print("Content-Length: " + payload.length());
                pw.print(EOL);
                pw.print(EOL);
                pw.print(payload);
                pw.print(EOL);
            }
        }

        @Override
        public void close() throws Exception {
            socket.close();
        }
    }
}
