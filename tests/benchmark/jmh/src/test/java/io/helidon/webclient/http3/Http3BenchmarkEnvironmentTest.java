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

package io.helidon.webclient.http3;

import java.time.Duration;

import io.helidon.http.Status;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class Http3BenchmarkEnvironmentTest {
    @Test
    void clientUsesBoundAddressWithLocalhostIdentity() throws Exception {
        try (var environment = Http3BenchmarkEnvironment.create(routing -> routing
                .get("/identity", (request, response) -> response.send(request.requestedUri().host())))) {
            var client = environment.client(environment.baseUri(environment.port()), Duration.ofSeconds(5));
            try (AutoCloseable _ = client::closeResource) {
                var configuration = client.prototype();
                assertThat(configuration.baseUri().orElseThrow().host(), is("localhost"));
                assertThat(configuration.tls().sslParameters().getEndpointIdentificationAlgorithm(), is("HTTPS"));
                assertThat(configuration.dnsResolver().resolveAddress("localhost", configuration.dnsAddressLookup()),
                           is(environment.address()));
                try (var response = client.get("/identity").request()) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                    assertThat(response.as(String.class), is("localhost"));
                }
            }
        }
    }
}
