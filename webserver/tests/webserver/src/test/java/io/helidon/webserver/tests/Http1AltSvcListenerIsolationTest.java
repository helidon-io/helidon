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

package io.helidon.webserver.tests;

import io.helidon.http.HeaderNames;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.AltSvcConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

class Http1AltSvcListenerIsolationTest {
    @Test
    void shouldResolveDynamicPortPerListener() {
        AltSvcConfig altSvcConfig = AltSvcConfig.builder().buildPrototype();
        Http1Config http1Config = Http1Config.builder()
                .altSvc(altSvcConfig)
                .build();
        Http1ConnectionSelector selector = Http1ConnectionSelector.builder()
                .config(http1Config)
                .build();
        WebServer server = WebServer.builder()
                .port(0)
                .protocolsDiscoverServices(false)
                .addConnectionSelector(selector)
                .routing(HttpRouting.builder().get("/", (req, res) -> res.send()))
                .putSocket("second", listener -> listener
                        .port(0)
                        .protocolsDiscoverServices(false)
                        .addConnectionSelector(selector)
                        .routing(HttpRouting.builder().get("/", (req, res) -> res.send())))
                .build()
                .start();
        int firstPort = server.port();
        int secondPort = server.port("second");
        assertThat(firstPort, not(secondPort));

        Http1Client firstClient = client(firstPort);
        Http1Client secondClient = client(secondPort);
        try {
            assertAltSvc(firstClient, firstPort);
            assertAltSvc(secondClient, secondPort);
        } finally {
            firstClient.closeResource();
            secondClient.closeResource();
            server.stop();
        }
    }

    private static Http1Client client(int port) {
        return Http1Client.builder()
                .baseUri("http://localhost:" + port)
                .build();
    }

    private static void assertAltSvc(Http1Client client, int port) {
        try (Http1ClientResponse response = client.get("/").request()) {
            assertThat(response.headers().first(HeaderNames.ALT_SVC).orElse(null), is("h3=\":" + port + "\""));
        }
    }
}
