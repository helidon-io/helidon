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

package io.helidon.nima.tests.integration.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.helidon.common.http.Http;
import io.helidon.common.http.Http.HeaderValues;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webclient.ClientResponse;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientRequest;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.http.HttpRouting;

import org.junit.jupiter.api.RepeatedTest;

import static io.helidon.common.testing.http.HttpHeaderMatcher.hasHeader;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class KeepAliveTest {
    private final Http1Client webClient;

    KeepAliveTest(Http1Client client) {
        this.webClient = client;
    }

    @SetUpRoute
    static void router(HttpRouting.Builder router) {
        router.route(Http.Method.PUT, "/plain", (req, res) -> {
            try (InputStream in = req.content().inputStream()) {
                byte[] buffer = new byte[128];
                while (in.read(buffer) > 0) {
                    // just ignore it
                }
                res.send("done");
            } catch (Exception e) {
                e.printStackTrace();
                res.status(Http.Status.INTERNAL_SERVER_ERROR_500)
                        .send(e.getMessage());
            }
        }).route(Http.Method.PUT, "/close", (req, res) -> {
            byte[] buffer = new byte[10];
            try (InputStream in = req.content().inputStream()) {
                in.read(buffer);
                throw new RuntimeException("BOOM!");
            } catch (IOException e) {
                res.status(Http.Status.INTERNAL_SERVER_ERROR_500)
                        .send(e.getMessage());
            }
        });
    }

    @RepeatedTest(100)
    void sendWithKeepAlive() {
        try (ClientResponse response = testCall(webClient, true, "/plain", 200)) {
            assertThat(response.headers(), hasHeader(HeaderValues.CONNECTION_KEEP_ALIVE));
        }

    }

    @RepeatedTest(100)
    void sendWithoutKeepAlive() {
        try (ClientResponse response = testCall(webClient, false, "/plain", 200)) {
            assertThat(response.headers(), not(hasHeader(HeaderValues.CONNECTION_KEEP_ALIVE)));
        }
    }

    @RepeatedTest(100)
    void sendWithKeepAliveExpectKeepAlive() {
        // we attempt to fully consume request entity, if succeeded, we keep connection keep-alive
        try (ClientResponse response = testCall(webClient, true, "/close", 500)) {
            assertThat(response.headers(), hasHeader(HeaderValues.CONNECTION_KEEP_ALIVE));
        }
    }

    private static ClientResponse testCall(Http1Client client,
                                           boolean keepAlive,
                                           String path,
                                           int expectedStatus) {

        Http1ClientRequest request = client.method(Http.Method.PUT)
                .uri(path);

        if (!keepAlive) {
            request.header(HeaderValues.CONNECTION_CLOSE);
        }

        Http1ClientResponse response = request
                .outputStream(it -> {
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.close();
                });

        assertThat(response.status(), is(Http.Status.create(expectedStatus)));

        return response;
    }
}
