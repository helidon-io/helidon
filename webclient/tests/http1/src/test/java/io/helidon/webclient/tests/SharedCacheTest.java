/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webclient.tests;

import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http1.Http1Route;

import org.junit.jupiter.api.Test;

import static io.helidon.http.Method.POST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SharedCacheTest {
    @Test
    void cacheHttp1WithServerRestart() {
        WebServer webServer = null;
        try {
            var routing = HttpRouting.builder()
                    .route(Http1Route.route(POST, "/", (req, res) -> res.send()));

            webServer = WebServer.builder()
                    .routing(routing)
                    .build()
                    .start();

            int port = webServer.port();

            WebClient webClient = WebClient.builder()
                    .keepAlive(true)
                    .baseUri("http://localhost:" + port + "/")
                    .build();

            try (var res = webClient.post().submit("WHATEVER")) {
                assertThat(res.status(), is(Status.OK_200));
            }
            webServer.stop();
            webServer = WebServer.builder()
                    .port(port)
                    .routing(routing)
                    .build()
                    .start();

            try (var res = webClient.post().submit("WHATEVER")) {
                assertThat(res.status(), is(Status.OK_200));
            }
        } finally {
            if (webServer != null) {
                webServer.stop();
            }
        }
    }

    @Test
    void cacheHttp1NoRestart() {
        HeaderName clientPortHeader = HeaderNames.create("client-port");
        WebServer webServer = null;
        try {
            var routing = HttpRouting.builder()
                    .route(Http1Route.route(POST, "/", (req, res) -> {
                        res.header(clientPortHeader, String.valueOf(req.remotePeer().port()));
                        res.send();
                    }));

            webServer = WebServer.builder()
                    .routing(routing)
                    .build()
                    .start();

            int port = webServer.port();

            WebClient webClient = WebClient.builder()
                    .keepAlive(true)
                    .baseUri("http://localhost:" + port)
                    .build();

            Integer firstReqClientPort;
            try (var res = webClient.post().submit("WHATEVER")) {
                firstReqClientPort = res.headers().get(clientPortHeader).get(Integer.TYPE);
                assertThat(res.status(), is(Status.OK_200));
            }

            // with global connection cache is noop
            webClient.closeResource();

            Integer secondReqClientPort;
            try (var res = webClient.post().submit("WHATEVER")) {
                secondReqClientPort = res.headers().get(clientPortHeader).get(Integer.TYPE);
                assertThat(res.status(), is(Status.OK_200));
            }

            assertThat("In case of cached connection client port must be the same.",
                       secondReqClientPort,
                       is(firstReqClientPort));
        } finally {
            if (webServer != null) {
                webServer.stop();
            }
        }
    }

    @Test
    void clientCache() {
        HeaderName clientPortHeader = HeaderNames.create("client-port");
        WebServer webServer = null;
        try {
            var routing = HttpRouting.builder()
                    .route(Http1Route.route(POST, "/", (req, res) -> {
                        res.header(clientPortHeader, String.valueOf(req.remotePeer().port()));
                        res.send();
                    }));

            webServer = WebServer.builder()
                    .routing(routing)
                    .build()
                    .start();

            int port = webServer.port();

            WebClient webClient = WebClient.builder()
                    .shareConnectionCache(false)
                    .keepAlive(true)
                    .baseUri("http://localhost:" + port)
                    .build();

            Integer firstReqClientPort;
            try (var res = webClient.post().submit("WHATEVER")) {
                firstReqClientPort = res.headers().get(clientPortHeader).get(Integer.TYPE);
                assertThat(res.status(), is(Status.OK_200));
            }

            Integer secondReqClientPort;
            try (var res = webClient.post().submit("WHATEVER")) {
                secondReqClientPort = res.headers().get(clientPortHeader).get(Integer.TYPE);
                assertThat(res.status(), is(Status.OK_200));
            }

            assertThat("In case of cached connection client port must be the same.",
                       secondReqClientPort,
                       is(firstReqClientPort));
        } finally {
            if (webServer != null) {
                webServer.stop();
            }
        }
    }

    @Test
    void clientCacheClosed() {
        HeaderName clientPortHeader = HeaderNames.create("client-port");
        WebServer webServer = null;
        try {
            var routing = HttpRouting.builder()
                    .route(Http1Route.route(POST, "/", (req, res) -> {
                        res.header(clientPortHeader, String.valueOf(req.remotePeer().port()));
                        res.send();
                    }));

            webServer = WebServer.builder()
                    .routing(routing)
                    .build()
                    .start();

            int port = webServer.port();

            WebClient webClient = WebClient.builder()
                    .shareConnectionCache(false)
                    .keepAlive(true)
                    .baseUri("http://localhost:" + port)
                    .build();

            try (var res = webClient.post().submit("WHATEVER")) {
                res.headers().get(clientPortHeader).get(Integer.TYPE);
                assertThat(res.status(), is(Status.OK_200));
            }

            webClient.closeResource();

            IllegalStateException e = assertThrows(IllegalStateException.class,
                                                   () -> {
                                                       try (var res = webClient.post().submit("WHATEVER")) {
                                                           res.headers().get(clientPortHeader).get(Integer.TYPE);
                                                       }
                                                   });
            assertThat(e.getMessage(), is("Connection cache is closed"));

        } finally {
            if (webServer != null) {
                webServer.stop();
            }
        }
    }
}
