/*
 * Copyright (c) 2017, 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests;

import java.util.List;

import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests specific header expectation from 204 NO CONTENT status code together
 * with {@link io.helidon.webclient.http1.Http1Client}.
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
                .get("/no-entity/204", (req, res) -> sendEmptyWithTrailer(res, Status.NO_CONTENT_204))
                .get("/no-entity/205", (req, res) -> sendEmptyWithTrailer(res, Status.RESET_CONTENT_205))
                .get("/no-entity/304", (req, res) -> sendEmptyWithTrailer(res, Status.NOT_MODIFIED_304))
                .put("/", (req, res) -> {
                    String ignored = req.content().as(String.class);
                    res.status(Status.NO_CONTENT_204).send();
                });
    }

    @Test
    void callPutAndGet() {
        try (HttpClientResponse response = client.method(Method.PUT)
                .submit("test call")) {

            assertThat(response.status(), is(Status.NO_CONTENT_204));
        }

        assertThat(client.get().requestEntity(String.class), is("test"));
    }

    @Test
    void noEntityStatusesWithDeclaredTrailer() {
        for (Status status : List.of(Status.NO_CONTENT_204, Status.RESET_CONTENT_205, Status.NOT_MODIFIED_304)) {
            try (HttpClientResponse response = client.get("/no-entity/" + status.code()).request()) {
                assertThat("Response status for " + status, response.status(), is(status));
                assertThat("Transfer-Encoding for " + status,
                           response.headers().contains(HeaderNames.TRANSFER_ENCODING), is(false));
            }
            assertThat("Subsequent response after " + status, client.get().requestEntity(String.class), is("test"));
        }
    }

    private static void sendEmptyWithTrailer(ServerResponse response, Status status) {
        response.header(HeaderNames.TRAILER, "x-result");
        response.status(status).send();
    }
}
