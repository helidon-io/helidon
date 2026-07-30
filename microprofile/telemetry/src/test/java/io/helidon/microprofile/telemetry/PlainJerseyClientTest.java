/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.microprofile.telemetry;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class PlainJerseyClientTest {
    @Test
    void plainJerseyClientSkipsCdiDependentTelemetryFilter() {
        try (Client client = ClientBuilder.newBuilder().register(new SuccessfulResponseFilter()).build()) {
            for (int i = 0; i < 5; i++) {
                try (Response response = client.target("http://localhost/unused").request().get()) {
                    assertThat(response.getStatus(), is(200));
                    assertThat(response.readEntity(String.class), is("pong"));
                }
            }
        }
    }

    private static class SuccessfulResponseFilter implements ClientRequestFilter {
        @Override
        public void filter(ClientRequestContext requestContext) {
            requestContext.abortWith(Response.ok("pong").build());
        }
    }
}
