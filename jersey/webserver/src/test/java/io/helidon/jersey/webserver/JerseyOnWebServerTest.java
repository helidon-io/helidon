/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.jersey.webserver;

import io.helidon.common.config.Config;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
public class JerseyOnWebServerTest {
    private final Http1Client client;

    public JerseyOnWebServerTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    public static void routing(HttpRouting.Builder routing) {
        ResourceConfig resourceConfig = new ResourceConfig(JaxRsEndpoint.class);
        routing.register("/jersey", JaxRsService.create(Config.empty(), resourceConfig));
    }

    @Test
    public void testEndpoint() {
        var response = client.get("/jersey/greet")
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("Hello World!"));
    }

    @Path("/greet")
    public static class JaxRsEndpoint {
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String greet() {
            return "Hello World!";
        }
    }
}
