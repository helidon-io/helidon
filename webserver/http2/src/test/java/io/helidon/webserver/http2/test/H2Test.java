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
package io.helidon.webserver.http2.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import io.helidon.common.LogConfig;
import io.helidon.common.http.Http;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.Http1Route;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http2.Http2Route;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import static io.helidon.common.http.Http.Method.GET;

class H2Test {

    private static final int HTTP2_FRAME_HEADERS = 1;
    private static final int HTTP2_FRAME_SETTINGS = 4;
    private static final int HTTP2_FRAME_GO_AWAY = 7;
    private static final int HTTP2_FRAME_WINDOW_UPDATE = 8;
    private static final int HTTP2_FLAG_END_HEADERS = 4;
    private static final int HTTP2_SETTING_INITIAL_WINDOW_SIZE = 4;
    private static final int HTTP2_ERROR_FLOW_CONTROL = 3;
    private static final int HTTP2_DEFAULT_WINDOW_SIZE = 65_535;
    private static final byte[] HTTP2_PREFACE = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] POST_HEADERS = new byte[] {
            (byte) 0x83, // :method POST
            (byte) 0x84, // :path /
            (byte) 0x86, // :scheme http
            (byte) 0x41, // :authority literal
            (byte) 0x09,
            (byte) 'l',
            (byte) 'o',
            (byte) 'c',
            (byte) 'a',
            (byte) 'l',
            (byte) 'h',
            (byte) 'o',
            (byte) 's',
            (byte) 't'
    };

    private static WebServer webServer;
    private static HttpClient httpClient;
    private static WebClient webClient;

    @BeforeAll
    public static void startServer() throws Exception {
        LogConfig.configureRuntime();
        webServer = WebServer.builder()
                .defaultSocket(s -> s
                        .bindAddress("localhost")
                        .port(0)
                )
                .routing(r -> r
                        .get("/", (req, res) -> res.send("HTTP Version " + req.version()))
                        .route(Http1Route.route(GET, "/versionspecific", (req, res) -> res.send("HTTP/1.1 route")))
                        .route(Http2Route.route(GET, "/versionspecific", (req, res) -> res.send("HTTP/2 route")))

                        .route(Http1Route.route(GET, "/versionspecific1", (req, res) -> res.send("HTTP/1.1 route")))
                        .route(Http2Route.route(GET, "/versionspecific2", (req, res) -> res.send("HTTP/2 route")))
                )
                .build()
                .start()
                .await(Duration.ofSeconds(10));

        httpClient = HttpClient.newHttpClient();
        webClient = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port())
                .build();
    }

    @AfterAll
    static void afterAll() {
        webServer.shutdown().await(Duration.ofSeconds(10));
    }

    @Test
    void genericHttp20() throws IOException, InterruptedException {
        assertThat(httpClientGet("/", HttpClient.Version.HTTP_2).body(), is("HTTP Version V2_0"));
        assertThat(webClientGet("/", Http.Version.V2_0).content().as(String.class).await(), is("HTTP Version V2_0"));
    }

    @Test
    void genericHttp11() throws IOException, InterruptedException {
        assertThat(httpClientGet("/", HttpClient.Version.HTTP_1_1).body(), is("HTTP Version V1_1"));
        assertThat(webClientGet("/", Http.Version.V1_1).content().as(String.class).await(), is("HTTP Version V1_1"));
    }

    @Test
    void oversizedInitialWindowSizeRejectedWithFlowControlGoAway() throws IOException {
        try (Socket socket = new Socket("localhost", webServer.port())) {
            socket.setSoTimeout(5_000);

            OutputStream output = socket.getOutputStream();
            output.write(HTTP2_PREFACE);
            writeSettings(output, HTTP2_SETTING_INITIAL_WINDOW_SIZE, 0xFFFF_FFFFL);
            output.flush();

            Http2Frame frame = readUntilFrame(socket.getInputStream(), HTTP2_FRAME_GO_AWAY);
            assertThat(frame.errorCode(), is(HTTP2_ERROR_FLOW_CONTROL));
        }
    }

    @Test
    void activeStreamInitialWindowSizeOverflowRejectedWithFlowControlGoAway() throws IOException {
        try (Socket socket = new Socket("localhost", webServer.port())) {
            socket.setSoTimeout(5_000);

            OutputStream output = socket.getOutputStream();
            output.write(HTTP2_PREFACE);
            writeSettings(output);
            writeFrame(output, HTTP2_FRAME_HEADERS, HTTP2_FLAG_END_HEADERS, 1, POST_HEADERS);
            writeWindowUpdate(output, 1, Integer.MAX_VALUE - HTTP2_DEFAULT_WINDOW_SIZE);
            writeSettings(output, HTTP2_SETTING_INITIAL_WINDOW_SIZE, HTTP2_DEFAULT_WINDOW_SIZE + 1L);
            output.flush();

            Http2Frame frame = readUntilFrame(socket.getInputStream(), HTTP2_FRAME_GO_AWAY);
            assertThat(frame.errorCode(), is(HTTP2_ERROR_FLOW_CONTROL));
        }
    }

    @Test
    void versionSpecificHttp11() throws IOException, InterruptedException {
        assertThat(httpClientGet("/versionspecific", HttpClient.Version.HTTP_1_1).body(), is("HTTP/1.1 route"));
        assertThat(webClientGet("/versionspecific", Http.Version.V1_1).content().as(String.class).await(), is("HTTP/1.1 route"));
    }

    @Test
    void versionSpecificHttp20() throws IOException, InterruptedException {
        assertThat(httpClientGet("/versionspecific", HttpClient.Version.HTTP_2).body(), is("HTTP/2 route"));
        assertThat(webClientGet("/versionspecific", Http.Version.V2_0).content().as(String.class).await(), is("HTTP/2 route"));
    }

    @Test
    void versionSpecificHttp11Negative() throws IOException, InterruptedException {
        assertThat(httpClientGet("/versionspecific1", HttpClient.Version.HTTP_2).statusCode(), is(404));
        assertThat(webClientGet("/versionspecific1", Http.Version.V2_0).status().code(), is(404));
    }

    @Test
    void versionSpecificHttp20Negative() throws IOException, InterruptedException {
        assertThat(httpClientGet("/versionspecific2", HttpClient.Version.HTTP_1_1).statusCode(), is(404));
        assertThat(webClientGet("/versionspecific2", Http.Version.V1_1).status().code(), is(404));
    }


    private HttpResponse<String> httpClientGet(String path, HttpClient.Version version) throws IOException, InterruptedException {
        return httpClient.send(HttpRequest.newBuilder()
                .version(version)
                .uri(URI.create("http://localhost:" + webServer.port() + path))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void writeSettings(OutputStream output) throws IOException {
        writeFrame(output, HTTP2_FRAME_SETTINGS, 0, 0, new byte[0]);
    }

    private static void writeSettings(OutputStream output, int id, long value) throws IOException {
        byte[] payload = new byte[] {
                (byte) (id >>> 8),
                (byte) id,
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value
        };
        writeFrame(output, HTTP2_FRAME_SETTINGS, 0, 0, payload);
    }

    private static void writeWindowUpdate(OutputStream output, int streamId, int increment) throws IOException {
        byte[] payload = new byte[] {
                (byte) (increment >>> 24),
                (byte) (increment >>> 16),
                (byte) (increment >>> 8),
                (byte) increment
        };
        writeFrame(output, HTTP2_FRAME_WINDOW_UPDATE, 0, streamId, payload);
    }

    private static void writeFrame(OutputStream output, int type, int flags, int streamId, byte[] payload) throws IOException {
        int length = payload.length;
        output.write(new byte[] {
                (byte) (length >>> 16),
                (byte) (length >>> 8),
                (byte) length,
                (byte) type,
                (byte) flags,
                (byte) (streamId >>> 24),
                (byte) (streamId >>> 16),
                (byte) (streamId >>> 8),
                (byte) streamId
        });
        output.write(payload);
    }

    private static Http2Frame readUntilFrame(InputStream input, int expectedType) throws IOException {
        while (true) {
            Http2Frame frame = readFrame(input);
            if (frame.type == expectedType) {
                return frame;
            }
        }
    }

    private static Http2Frame readFrame(InputStream input) throws IOException {
        byte[] header = input.readNBytes(9);
        if (header.length != 9) {
            throw new IOException("Incomplete HTTP/2 frame header");
        }
        int length = ((header[0] & 0xFF) << 16)
                | ((header[1] & 0xFF) << 8)
                | (header[2] & 0xFF);
        int type = header[3] & 0xFF;
        byte[] payload = input.readNBytes(length);
        if (payload.length != length) {
            throw new IOException("Incomplete HTTP/2 frame payload");
        }
        return new Http2Frame(type, payload);
    }

    private WebClientResponse webClientGet(String path, Http.Version version) {
        return webClient.get()
                .httpVersion(version)
                .path(path)
                .request()
                .await(Duration.ofSeconds(10));
    }

    private static final class Http2Frame {
        private final int type;
        private final byte[] payload;

        private Http2Frame(int type, byte[] payload) {
            this.type = type;
            this.payload = payload;
        }

        private int errorCode() {
            return ((payload[4] & 0xFF) << 24)
                    | ((payload[5] & 0xFF) << 16)
                    | ((payload[6] & 0xFF) << 8)
                    | (payload[7] & 0xFF);
        }
    }
}
