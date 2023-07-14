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
package io.helidon.tests.integration.yamlparsing;


import io.helidon.common.http.Http;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.http.HttpRouting;

import org.junit.jupiter.api.Test;
import jakarta.json.JsonObject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

abstract class AbstractMainTest {
    private final Http1Client client;

    protected AbstractMainTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        Main.routing(builder);
    }

    @Test
    void testRootRoute() {
        try (Http1ClientResponse response = client.get("/greet").request()) {
            assertThat(response.status(), is(Http.Status.OK_200));
            JsonObject json = response.as(JsonObject.class);
            assertThat(json.getString("message"), is("Hello World!"));
        }
    }

    @Test
    void testOpenApi() {
        try (Http1ClientResponse response = client.get("/openapi").request()) {
            assertThat("/openapi status", response.status(), is(Http.Status.OK_200));
            assertThat("/openapi content", response.as(String.class), containsString("title: Swagger Petstore"));
        }
    }

}
