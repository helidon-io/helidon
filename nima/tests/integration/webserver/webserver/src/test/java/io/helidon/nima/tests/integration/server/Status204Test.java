/*
 * Copyright (c) 2017, 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.tests.integration.server;

import io.helidon.common.http.Http;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webclient.api.HttpClientResponse;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webserver.http.HttpRules;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests specific header expectation from 204 NO CONTENT status code together
 * with {@link io.helidon.nima.webclient.http1.Http1Client}.
 */
@ServerTest
class Status204Test {

    private final Http1Client client;

    Status204Test(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/", (req, res) -> res.send("test"))
                .put("/", (req, res) -> {
                    String ignored = req.content().as(String.class);
                    res.status(Http.Status.NO_CONTENT_204).send();
                });
    }

    @Test
    void callPutAndGet() {
        try (HttpClientResponse response = client.method(Http.Method.PUT)
                .submit("test call")) {

            assertThat(response.status(), is(Http.Status.NO_CONTENT_204));
        }

        assertThat(client.get().request(String.class), is("test"));
    }
}
