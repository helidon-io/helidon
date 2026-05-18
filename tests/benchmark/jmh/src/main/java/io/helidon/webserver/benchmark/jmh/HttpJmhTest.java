/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.benchmark.jmh;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http1.Http1Route;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class HttpJmhTest {
    private static final String SERVER_HOST = "127.0.0.1";
    private static final int SOCKET_READ_TIMEOUT_MILLIS = 5_000;
    private static final Header CONTENT_TYPE = HeaderValues.createCached(HeaderNames.CONTENT_TYPE,
                                                                         "text/plain; charset=UTF-8");
    private static final Header CONTENT_LENGTH = HeaderValues.createCached(HeaderNames.CONTENT_LENGTH, "13");
    private static final Header SERVER = HeaderValues.createCached(HeaderNames.SERVER, "Helidon");
    private static final byte[] RESPONSE_BYTES = "Hello, World!".getBytes(StandardCharsets.UTF_8);
    private static final byte[] HTTP_1_CLOSE_REQUEST = """
            GET /plaintext HTTP/1.1\r
            Host: localhost\r
            Connection: close\r
            \r
            """.getBytes(StandardCharsets.US_ASCII);
    private WebServer server;
    private int serverPort;
    private HttpClient http1Client;
    private HttpClient http2Client;

    @Setup
    public void setup() {
        LogConfig.configureRuntime();

        server = WebServer.builder()
                .connectionOptions(builder -> builder
                        .readTimeout(Duration.ZERO)
                        .connectTimeout(Duration.ZERO)
                        .socketSendBufferSize(64000)
                        .socketReceiveBufferSize(64000))
                .writeQueueLength(4000)
                .host(SERVER_HOST)
                .backlog(8192)
                .routing(router -> router.route(Http1Route.route(Method.GET, "/plaintext", new PlaintextHandler())))
                .build()
                .start();

        serverPort = server.port();

        http2Client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        http1Client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @TearDown
    public void tearDown() {
        server.stop();
    }

    @Benchmark
    public void http1(Blackhole bh) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://" + SERVER_HOST + ":" + serverPort + "/plaintext"))
                .build();
        HttpResponse<byte[]> response = http1Client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        bh.consume(response);
    }

    @Benchmark
    public void http1NewConnection(Blackhole bh) throws IOException {
        byte[] responseBuffer = new byte[1024];
        try (Socket socket = new Socket(SERVER_HOST, serverPort)) {
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(SOCKET_READ_TIMEOUT_MILLIS);
            OutputStream output = socket.getOutputStream();
            output.write(HTTP_1_CLOSE_REQUEST);
            output.flush();

            int bytesRead = readResponse(socket.getInputStream(), responseBuffer);
            bh.consume(bytesRead);
            bh.consume(responseBuffer);
        }
    }

    @Benchmark
    public void http2(Blackhole bh) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://" + SERVER_HOST + ":" + serverPort + "/plaintext"))
                .build();
        HttpResponse<byte[]> response = http2Client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        bh.consume(response);
    }

    private static int readResponse(InputStream input, byte[] responseBuffer) throws IOException {
        int bytesRead = 0;
        int bodyStart = -1;
        while (bytesRead < responseBuffer.length) {
            int read = input.read(responseBuffer, bytesRead, responseBuffer.length - bytesRead);
            if (read == -1) {
                throw new EOFException("Response ended before complete body was received");
            }
            bytesRead += read;
            if (bodyStart == -1) {
                bodyStart = bodyStart(responseBuffer, bytesRead);
            }
            if (bodyStart != -1 && bytesRead >= bodyStart + RESPONSE_BYTES.length) {
                if (!bodyMatches(responseBuffer, bodyStart)) {
                    throw new IOException("Unexpected response body");
                }
                return bytesRead;
            }
        }
        throw new IOException("Response exceeded benchmark buffer before complete body was received");
    }

    private static int bodyStart(byte[] responseBuffer, int bytesRead) {
        for (int i = 3; i < bytesRead; i++) {
            if (responseBuffer[i - 3] == '\r'
                    && responseBuffer[i - 2] == '\n'
                    && responseBuffer[i - 1] == '\r'
                    && responseBuffer[i] == '\n') {
                return i + 1;
            }
        }
        return -1;
    }

    private static boolean bodyMatches(byte[] responseBuffer, int bodyStart) {
        for (int i = 0; i < RESPONSE_BYTES.length; i++) {
            if (responseBuffer[bodyStart + i] != RESPONSE_BYTES[i]) {
                return false;
            }
        }
        return true;
    }

    private static class PlaintextHandler implements Handler {
        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            res.header(CONTENT_LENGTH);
            res.header(CONTENT_TYPE);
            res.header(SERVER);
            res.send(RESPONSE_BYTES);
        }
    }
}
