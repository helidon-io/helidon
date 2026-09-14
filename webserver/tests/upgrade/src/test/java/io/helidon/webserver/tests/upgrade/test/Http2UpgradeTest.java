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

package io.helidon.webserver.tests.upgrade.test;

import java.time.Duration;

import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.logging.common.LogConfig;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.Http2Route;

import org.junit.jupiter.api.Test;

import static io.helidon.http.Method.GET;
import static io.helidon.http.Method.POST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

class Http2UpgradeTest {
    private static final String HTTP2_SETTINGS = "AAEAABAAAAIAAAAB";

    @Test
    void testHttp2UpgradeWithPayload() {
        LogConfig.configureRuntime();

        WebServer webServer = null;
        try {
            // define HTTP/2 route only
            var routing = HttpRouting.builder()
                    .route(Http2Route.route(POST, "/echo", (req, res) -> {
                        String s = req.content().as(String.class);
                        res.send(s);
                    }));

            // start server and get port
            webServer = WebServer.builder()
                    .writeQueueLength(2)
                    .smartAsyncWrites(true)
                    .routing(routing)
                    .build()
                    .start();
            int port = webServer.port();

            // verify not found when payload in initial request and no HTTP/1 route
            Http1Client http1Client = Http1Client.builder()
                    .baseUri("http://localhost:" + port + "/echo")
                    .build();
            try (var res = http1Client.post()
                    .header(HeaderNames.UPGRADE, "h2c")     // upgrade pretend
                    .submit("payload")) {
                assertThat(res.status(), is(Status.NOT_FOUND_404));
            }

            // verify success with smart client when payload is sent after upgrade
            Http2Client http2Client = Http2Client.builder()
                    .protocolConfig(Http2ClientProtocolConfig.builder()
                                            .priorKnowledge(false)
                                            .build())
                    .baseUri("http://localhost:" + port + "/echo")
                    .build();
            try (var res = http2Client.post().submit("payload")) {
                assertThat(res.status(), is(Status.OK_200));
                assertThat(res.entity().as(String.class), is("payload"));
            }
        } finally {
            if (webServer != null) {
                webServer.stop();
            }
        }
    }

    @Test
    void testHttp2UpgradeContentLengthFraming() throws Exception {
        LogConfig.configureRuntime();

        WebServer webServer = null;
        try {
            var routing = HttpRouting.builder()
                    .route(Http2Route.route(POST, "/echo", (req, res) -> {
                        String s = req.content().as(String.class);
                        res.send(s);
                    }))
                    .route(Http2Route.route(GET, "/ping", (req, res) -> res.send("pong")));

            webServer = WebServer.builder()
                    .routing(routing)
                    .build()
                    .start();
            int port = webServer.port();

            Http2Client http2Client = Http2Client.builder()
                    .protocolConfig(Http2ClientProtocolConfig.builder()
                                            .priorKnowledge(false)
                                            .build())
                    .baseUri("http://localhost:" + port)
                    .build();
            try (var res = http2Client.get("/ping")
                    .header(HeaderNames.CONTENT_LENGTH, "0")
                    .request()) {
                assertThat(res.status(), is(Status.OK_200));
                assertThat(res.entity().as(String.class), is("pong"));
            }
            try (var res = http2Client.get("/ping").request()) {
                assertThat(res.status(), is(Status.OK_200));
                assertThat(res.entity().as(String.class), is("pong"));
            }

            try (SocketHttpClient socketClient = SocketHttpClient.create("localhost", port, Duration.ofSeconds(10))) {
                socketClient.requestRaw(h2cUpgradeRequest("Content-Length: 0, 0\r\n", ""));
                assertThat(socketClient.receive(), containsString("400 Bad Request"));
            }
            try (SocketHttpClient socketClient = SocketHttpClient.create("localhost", port, Duration.ofSeconds(10))) {
                socketClient.requestRaw(h2cUpgradeRequest("Content-Length: 5\r\n", "abcde"));
                assertThat(socketClient.receive(), containsString("404 Not Found"));
            }
            try (SocketHttpClient socketClient = SocketHttpClient.create("localhost", port, Duration.ofSeconds(10))) {
                socketClient.requestRaw(h2cUpgradeRequest("Content-Length: 5, 5\r\n", "abcde"));
                assertThat(socketClient.receive(), containsString("400 Bad Request"));
            }
            try (SocketHttpClient socketClient = SocketHttpClient.create("localhost", port, Duration.ofSeconds(10))) {
                socketClient.requestRaw(h2cUpgradeRequest("Content-Length: +5\r\n", ""));
                assertThat(socketClient.receive(), containsString("400 Bad Request"));
            }
            try (SocketHttpClient socketClient = SocketHttpClient.create("localhost", port, Duration.ofSeconds(10))) {
                socketClient.requestRaw(h2cUpgradeRequest("Content-Length: 0, 1\r\n", ""));
                assertThat(socketClient.receive(), containsString("400 Bad Request"));
            }
        } finally {
            if (webServer != null) {
                webServer.stop();
            }
        }
    }

    private static String h2cUpgradeRequest(String contentLengthHeader, String entity) {
        return "POST /echo HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Connection: Upgrade, HTTP2-Settings\r\n"
                + "Upgrade: h2c\r\n"
                + "HTTP2-Settings: " + HTTP2_SETTINGS + "\r\n"
                + contentLengthHeader
                + "\r\n"
                + entity;
    }
}
