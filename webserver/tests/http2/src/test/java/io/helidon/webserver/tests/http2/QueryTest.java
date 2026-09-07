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

package io.helidon.webserver.tests.http2;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
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
    private static final HeaderName CONNECTION_ID = HeaderNames.create("X-Connection-Id");
    private static final AtomicInteger INVALID_ROUTE_INVOCATIONS = new AtomicInteger();

    private final HttpClient client;
    private final URI uri;

    QueryTest(URI uri) {
        this.uri = uri;
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        routing.addFilter((chain, req, res) -> {
            res.header(CONNECTION_ID, req.socketId());
            chain.proceed();
        }).route(Method.QUERY, "/valid", (req, res) -> res.send(req.content().as(String.class)))
                .route(Method.QUERY, "/invalid", (_, res) -> {
                    INVALID_ROUTE_INVOCATIONS.incrementAndGet();
                    res.send("Unexpected route invocation");
                });
    }

    @Test
    void validQueryBodyRoutes() throws Exception {
        establishHttp2();
        assertValidQuery();
    }

    @Test
    void missingContentTypeIsBadRequestAndSessionRemainsUsable() throws Exception {
        assertBadRequestThenValid(HttpRequest.newBuilder()
                                          .uri(uri.resolve("/invalid")));
    }

    @Test
    void repeatedContentTypeIsBadRequestAndSessionRemainsUsable() throws Exception {
        assertBadRequestThenValid(HttpRequest.newBuilder()
                                          .uri(uri.resolve("/invalid"))
                                          .header("Content-Type", "text/plain")
                                          .header("Content-Type", "application/json"));
    }

    @Test
    void malformedContentTypeIsBadRequestAndSessionRemainsUsable() throws Exception {
        assertBadRequestThenValid(HttpRequest.newBuilder()
                                          .uri(uri.resolve("/invalid"))
                                          .header("Content-Type", "invalid"));
    }

    private void assertBadRequestThenValid(HttpRequest.Builder requestBuilder) throws Exception {
        establishHttp2();
        int invocationCount = INVALID_ROUTE_INVOCATIONS.get();
        HttpResponse<String> response = client.send(requestBuilder
                                                            .timeout(Duration.ofSeconds(5))
                                                            .method(Method.QUERY.text(),
                                                                    HttpRequest.BodyPublishers.ofString(QUERY))
                                                            .build(),
                                                    HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode(), is(Status.BAD_REQUEST_400.code()));
        assertThat(response.version(), is(HttpClient.Version.HTTP_2));
        assertThat(INVALID_ROUTE_INVOCATIONS.get(), is(invocationCount));
        String connectionId = response.headers().firstValue(CONNECTION_ID.lowerCase()).orElseThrow();
        HttpResponse<String> validResponse = assertValidQuery();
        assertThat(validResponse.headers().firstValue(CONNECTION_ID.lowerCase()).orElseThrow(), is(connectionId));
    }

    private HttpResponse<String> assertValidQuery() throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                                                            .uri(uri.resolve("/valid"))
                                                            .timeout(Duration.ofSeconds(5))
                                                            .header("Content-Type", "application/x-www-form-urlencoded")
                                                            .method(Method.QUERY.text(),
                                                                    HttpRequest.BodyPublishers.ofString(QUERY))
                                                            .build(),
                                                    HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode(), is(Status.OK_200.code()));
        assertThat(response.body(), is(QUERY));
        assertThat(response.version(), is(HttpClient.Version.HTTP_2));
        return response;
    }

    private void establishHttp2() throws IOException, InterruptedException {
        client.send(HttpRequest.newBuilder()
                            .uri(uri.resolve("/valid"))
                            .timeout(Duration.ofSeconds(5))
                            .HEAD()
                            .build(),
                    HttpResponse.BodyHandlers.discarding());
    }
}
