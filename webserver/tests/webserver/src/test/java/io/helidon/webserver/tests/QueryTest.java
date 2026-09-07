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

package io.helidon.webserver.tests;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class QueryTest {
    private static final String QUERY = "name=Joe";
    private static final AtomicInteger INVALID_ROUTE_INVOCATIONS = new AtomicInteger();

    private final URI uri;

    QueryTest(URI uri) {
        this.uri = uri;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        routing.route(Method.QUERY, "/valid", (req, res) -> res.send(req.content().as(String.class)))
                .route(Method.QUERY, "/invalid", (_, res) -> {
                    INVALID_ROUTE_INVOCATIONS.incrementAndGet();
                    res.send("Unexpected route invocation");
                });
    }

    @Test
    void validQueryBodyRoutes() throws Exception {
        try (SocketHttpClient client = socketClient()) {
            assertValidQuery(client);
        }
    }

    @Test
    void missingContentTypeIsBadRequestAndConnectionRemainsUsable() throws Exception {
        assertBadRequestThenValid(List.of());
    }

    @Test
    void repeatedContentTypeIsBadRequestAndConnectionRemainsUsable() throws Exception {
        assertBadRequestThenValid(List.of("Content-Type: text/plain", "Content-Type: application/json"));
    }

    @Test
    void malformedContentTypeIsBadRequestAndConnectionRemainsUsable() throws Exception {
        assertBadRequestThenValid(List.of("Content-Type: invalid"));
    }

    private void assertBadRequestThenValid(List<String> headers) throws Exception {
        int invocationCount = INVALID_ROUTE_INVOCATIONS.get();
        try (SocketHttpClient client = socketClient()) {
            String response = client.sendAndReceive(Method.QUERY, "/invalid", QUERY, headers);

            assertThat(SocketHttpClient.statusFromResponse(response), is(Status.BAD_REQUEST_400));
            assertThat(INVALID_ROUTE_INVOCATIONS.get(), is(invocationCount));
            assertValidQuery(client);
        }
    }

    private void assertValidQuery(SocketHttpClient client) {
        String response = client.sendAndReceive(Method.QUERY,
                                                "/valid",
                                                QUERY,
                                                List.of("Content-Type: application/x-www-form-urlencoded"));

        assertThat(SocketHttpClient.statusFromResponse(response), is(Status.OK_200));
        assertThat(SocketHttpClient.entityFromResponse(response, true), is(QUERY));
    }

    private SocketHttpClient socketClient() {
        return SocketHttpClient.create(uri.getHost(), uri.getPort(), Duration.ofSeconds(5));
    }
}
