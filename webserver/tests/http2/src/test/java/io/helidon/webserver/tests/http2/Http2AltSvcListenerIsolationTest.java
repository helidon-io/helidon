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

package io.helidon.webserver.tests.http2;

import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;
import io.helidon.webclient.http2.Http2ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.AltSvc;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2ConnectionSelector;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.http2.Http2Upgrader;

import org.junit.jupiter.api.Test;

import static io.helidon.http.Method.GET;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

class Http2AltSvcListenerIsolationTest {
    @Test
    void shouldResolveDynamicPortPerListenerWhenConnectionSelectorIsShared() {
        Http2Config http2Config = http2Config();
        Http2ConnectionSelector selector = Http2ConnectionSelector.builder()
                .http2Config(http2Config)
                .build();
        WebServer server = WebServer.builder()
                .port(0)
                .protocolsDiscoverServices(false)
                .addConnectionSelector(selector)
                .routing(Http2AltSvcListenerIsolationTest::routing)
                .putSocket("second", listener -> listener
                        .port(0)
                        .protocolsDiscoverServices(false)
                        .addConnectionSelector(selector)
                        .routing(Http2AltSvcListenerIsolationTest::routing))
                .build()
                .start();
        try {
            assertListenerSpecificHeaders(server, true);
        } finally {
            server.stop();
        }
    }

    @Test
    void shouldResolveDynamicPortPerListenerWhenUpgraderIsShared() {
        Http2Upgrader upgrader = Http2Upgrader.create(http2Config());
        WebServer server = WebServer.builder()
                .port(0)
                .protocolsDiscoverServices(false)
                .addConnectionSelector(Http1ConnectionSelector.builder()
                                               .config(Http1Config.create())
                                               .addUpgrader(upgrader)
                                               .build())
                .routing(Http2AltSvcListenerIsolationTest::routing)
                .putSocket("second", listener -> listener
                        .port(0)
                        .protocolsDiscoverServices(false)
                        .addConnectionSelector(Http1ConnectionSelector.builder()
                                                       .config(Http1Config.create())
                                                       .addUpgrader(upgrader)
                                                       .build())
                        .routing(Http2AltSvcListenerIsolationTest::routing))
                .build()
                .start();
        try {
            assertListenerSpecificHeaders(server, false);
        } finally {
            server.stop();
        }
    }

    private static Http2Config http2Config() {
        return Http2Config.builder()
                .altSvc(AltSvc.builder().buildPrototype())
                .build();
    }

    private static void routing(HttpRouting.Builder routing) {
        routing.route(Http2Route.route(GET, "/", (req, res) -> res.send()));
    }

    private static void assertListenerSpecificHeaders(WebServer server, boolean priorKnowledge) {
        int firstPort = server.port();
        int secondPort = server.port("second");
        assertThat(firstPort, not(secondPort));

        assertAltSvc(firstPort, priorKnowledge);
        assertAltSvc(secondPort, priorKnowledge);
    }

    private static void assertAltSvc(int port, boolean priorKnowledge) {
        Http2Client client = Http2Client.builder()
                .baseUri("http://localhost:" + port)
                .protocolConfig(Http2ClientProtocolConfig.builder()
                                        .priorKnowledge(priorKnowledge)
                                        .build())
                .build();
        try (Http2ClientResponse response = client.get("/").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat("Alt-Svc header from listener on port " + port,
                       response.headers().first(HeaderNames.ALT_SVC).orElse(null),
                       is("h3=\":" + port + "\""));
        } finally {
            client.closeResource();
        }
    }
}
