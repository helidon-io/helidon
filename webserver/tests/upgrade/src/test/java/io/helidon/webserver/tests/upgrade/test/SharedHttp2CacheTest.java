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

package io.helidon.webserver.tests.upgrade.test;

import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.logging.common.LogConfig;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.Http2Route;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static io.helidon.http.Method.POST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class SharedHttp2CacheTest {

    static Param[] params() {
        return new Param[] {
                new Param(true, true, true),
                new Param(true, true, false),
                new Param(true, false, false),
                new Param(false, false, true),
                new Param(false, true, true),
                new Param(false, true, false),
                new Param(true, false, true),
                new Param(false, false, false),
        };
    }

    @ParameterizedTest
    @MethodSource("params")
    void cacheHttp2WithServerRestart(Param param) {
        LogConfig.configureRuntime();
        HeaderName clientPortHeader = HeaderNames.create("client-port");
        WebServer webServer = null;
        try {
            var routing = HttpRouting.builder()
                    .route(Http2Route.route(POST, "/versionspecific", (req, res) -> {
                        req.content().consume(); // Workaround for #7427
                        res.header(clientPortHeader, String.valueOf(req.remotePeer().port()))
                                .send();
                    }));

            webServer = WebServer.builder()
                    .routing(routing)
                    .build()
                    .start();

            int port = webServer.port();

            Http2Client webClient = Http2Client.builder()
                    .protocolConfig(Http2ClientProtocolConfig.builder()
                                            .priorKnowledge(param.priorKnowledge())
                                            .ping(param.usePing())
                                            .build())
                    .baseUri("http://localhost:" + port + "/versionspecific")
                    .build();

            Integer firstReqClientPort;
            try (var res = webClient.post().submit("WHATEVER")) {
                firstReqClientPort = res.headers().get(clientPortHeader).get(Integer.TYPE);
                assertThat(res.status(), is(Status.OK_200));
            }

            if (param.restart()) {
                // Test severing cached connections
                webServer.stop();
                webServer = WebServer.builder()
                        .port(port)
                        .routing(routing)
                        .build()
                        .start();
            }

            Integer secondReqClientPort;
            try (var res = webClient.post().submit("WHATEVER")) {
                secondReqClientPort = res.headers().get(clientPortHeader).get(Integer.TYPE);
                assertThat(res.status(), is(Status.OK_200));
            }

            if (!param.restart()) {
                assertThat("In case of cached connection client port must be the same.",
                           secondReqClientPort,
                           is(firstReqClientPort));
            }

        } finally {
            if (webServer != null) {
                webServer.stop();
            }
        }
    }

    record Param(boolean usePing, boolean priorKnowledge, boolean restart) {
    }
}
