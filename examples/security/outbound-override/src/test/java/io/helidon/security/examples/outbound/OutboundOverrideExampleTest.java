/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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
package io.helidon.security.examples.outbound;

import java.net.URI;

import io.helidon.security.EndpointConfig;
import io.helidon.security.Security;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.security.WebClientSecurity;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Test of security override example.
 */
@ServerTest
public class OutboundOverrideExampleTest {

    private final Http1Client client;

    OutboundOverrideExampleTest(WebServer server, URI uri) {
        server.context().register(server);
        Security security = Security.builder()
                .addProvider(HttpBasicAuthProvider.builder().build())
                .build();
        client = Http1Client.builder()
                .baseUri(uri)
                .addService(WebClientSecurity.create(security))
                .build();
    }

    @SetUpServer
    public static void setup(WebServerConfig.Builder server) {
        OutboundOverrideExample.setup(server);
    }

    @Test
    public void testOverrideExample() {
        String value = client.get()
                .path("/override")
                .property(EndpointConfig.PROPERTY_OUTBOUND_ID, "jack")
                .property(EndpointConfig.PROPERTY_OUTBOUND_SECRET, "changeit")
                .requestEntity(String.class);

        assertThat(value, is("You are: jack, backend service returned: jill\n"));
    }

    @Test
    public void testPropagateExample() {
        String value = client.get()
                .path("/propagate")
                .property(EndpointConfig.PROPERTY_OUTBOUND_ID, "jack")
                .property(EndpointConfig.PROPERTY_OUTBOUND_SECRET, "changeit")
                .requestEntity(String.class);

        assertThat(value, is("You are: jack, backend service returned: jack\n"));
    }
}
