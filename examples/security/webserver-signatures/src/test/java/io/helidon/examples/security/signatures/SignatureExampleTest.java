/*
 * Copyright (c) 2018, 2024 Oracle and/or its affiliates.
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

package io.helidon.examples.security.signatures;

import java.net.URI;
import java.util.Set;

import io.helidon.security.Security;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webclient.security.WebClientSecurity;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.testing.junit5.ServerTest;

import org.junit.jupiter.api.Test;

import static io.helidon.security.EndpointConfig.PROPERTY_OUTBOUND_ID;
import static io.helidon.security.EndpointConfig.PROPERTY_OUTBOUND_SECRET;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
public abstract class SignatureExampleTest {

    private final Http1Client client;

    protected SignatureExampleTest(WebServer server, URI uri) {
        server.context().register(server);

        Security security = Security.builder()
                .addProvider(HttpBasicAuthProvider.builder().build())
                .build();

        client = Http1Client.builder()
                .addService(WebClientSecurity.create(security))
                .baseUri(uri)
                .build();
    }

    @Test
    public void testService1Hmac() {
        test("/service1", Set.of("user", "admin"), Set.of(), "Service1 - HMAC signature");
    }

    @Test
    public void testService1Rsa() {
        test("/service1-rsa", Set.of("user", "admin"), Set.of(), "Service1 - RSA signature");
    }

    private void test(String uri, Set<String> expectedRoles, Set<String> invalidRoles, String service) {
        try (Http1ClientResponse response = client.get(uri)
                .property(PROPERTY_OUTBOUND_ID, "jack")
                .property(PROPERTY_OUTBOUND_SECRET, "changeit")
                .request()) {

            assertThat(response.status().code(), is(200));

            String payload = response.as(String.class);

            // check login
            assertThat(payload, containsString("id='" + "jack" + "'"));

            // check roles
            expectedRoles.forEach(role -> assertThat(payload, containsString(":" + role)));
            invalidRoles.forEach(role -> assertThat(payload, not(containsString(":" + role))));
            assertThat(payload, containsString("id='" + service + "'"));
        }
    }
}
