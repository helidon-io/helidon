/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.examples.nima.observe;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.http.HttpRouting;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

abstract class AbstractObserveTest {
    private final Http1Client client;

    protected AbstractObserveTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        Config config = Config.create();

        ObserveMain.routing(config, builder);
    }

    @Test
    void testRootRoute() {
        String response = client.get("/")
                .request()
                .as(String.class);

        assertThat(response, is("Níma Works!"));
    }

    @Test
    void testConfigObserver() {
        try (Http1ClientResponse response = client.get("/observe/config/profile").request()) {
            // this requires basic authentication
            assertThat(response.status(), is(Http.Status.UNAUTHORIZED_401));
        }
    }

    @Test
    void testHealthObserver() {
        try (Http1ClientResponse response = client.get("/observe/health").request()) {
            assertThat(response.status(), is(Http.Status.NO_CONTENT_204));
        }
    }

    @Test
    void testInfoObserver() {
        try (Http1ClientResponse response = client.get("/observe/info").request()) {
            assertThat(response.status(), is(Http.Status.OK_200));
        }
    }
}
