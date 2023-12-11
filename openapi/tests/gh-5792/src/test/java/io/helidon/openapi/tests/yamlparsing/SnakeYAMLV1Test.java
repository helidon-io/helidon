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
package io.helidon.openapi.tests.yamlparsing;

import io.helidon.http.Status;
import io.helidon.openapi.OpenApiFeature;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.DirectClient;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@RoutingTest
class SnakeYAMLV1Test {

    private final Http1Client client;

    SnakeYAMLV1Test(DirectClient client) {
        this.client = client;
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        server.addFeature(OpenApiFeature.builder()
                                  .staticFile("target/test-classes/petstore.yaml")
                                  .build());
    }
    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
    }

    @Test
    void testOpenApi() {
        try (Http1ClientResponse response = client.get("/openapi").request()) {
            assertThat("/openapi status", response.status(), is(Status.OK_200));
            assertThat("/openapi content", response.as(String.class), containsString("title: Swagger Petstore"));
        }
    }
}
