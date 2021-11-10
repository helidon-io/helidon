/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.integration.jersey.client;

import java.io.IOException;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link ClientSecurityAutoDiscoverable}.
 */
class ClientSecurityAutoDiscoverableTest {
    @Test
    void testAutoDiscoverable() {
        UnitTestFilter unitTestFilter = new UnitTestFilter();
        Client client = ClientBuilder.newBuilder()
                .register(unitTestFilter)
                .build();
        WebTarget target = client.target("http://localhost:8080");
        Response response = target.request().get();

        assertThat("Our filter should have stopped processing", response.readEntity(String.class), is("Filtered"));
        assertThat("Security client filter should have been registered", unitTestFilter.registered, is(true));
    }

    private static class UnitTestFilter implements ClientRequestFilter {
        private volatile boolean registered;
        @Override
        public void filter(ClientRequestContext requestContext) throws IOException {
            registered = requestContext.getConfiguration().isRegistered(ClientSecurityFilter.class);
            requestContext.abortWith(Response.ok("Filtered").build());
        }
    }
}
