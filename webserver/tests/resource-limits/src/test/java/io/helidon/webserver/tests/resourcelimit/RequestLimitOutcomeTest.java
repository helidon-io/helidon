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

package io.helidon.webserver.tests.resourcelimit;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.concurrency.limits.LimitAlgorithm;
import io.helidon.http.BadRequestException;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2Headers;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.testing.junit5.http2.Http2TestClient;
import io.helidon.webserver.testing.junit5.http2.Http2TestConnection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class RequestLimitOutcomeTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final RecordingLimit LIMIT = new RecordingLimit();

    private final RecordingToken token = new RecordingToken();
    private final Http2Client client;

    RequestLimitOutcomeTest(Http2Client client) {
        this.client = client;
    }

    @SetUpServer
    static void serverSetup(WebServerConfig.Builder builder) {
        builder.concurrencyLimit(LIMIT);
    }

    @SetUpRoute
    static void routeSetup(HttpRouting.Builder routing) {
        routing.get("/success", (_, res) -> res.send("hello"))
                .get("/bad-request", (_, _) -> {
                    throw new BadRequestException("Invalid request");
                })
                .get("/mapped-error", (_, _) -> {
                    throw new IllegalArgumentException("Invalid argument");
                })
                .get("/server-error", (_, _) -> {
                    throw new IllegalStateException("Request failed");
                })
                .error(IllegalArgumentException.class, (_, res, _) -> res.status(Status.BAD_REQUEST_400).send());
    }

    @BeforeEach
    void beforeEach() {
        LIMIT.token = token;
    }

    @Test
    void successfulRequestCompletesPermit() throws InterruptedException {
        try (HttpClientResponse response = client.get("/success").request()) {
            assertThat(response.status(), is(Status.OK_200));
        }
        assertOutcome(1, 0, 0);
    }

    @Test
    void notFoundRequestIgnoresPermit() throws InterruptedException {
        try (HttpClientResponse response = client.get("/missing").request()) {
            assertThat(response.status(), is(Status.NOT_FOUND_404));
        }
        assertOutcome(0, 1, 0);
    }

    @Test
    void mappedClientErrorDropsPermit() throws InterruptedException {
        try (HttpClientResponse response = client.get("/mapped-error").request()) {
            assertThat(response.status(), is(Status.BAD_REQUEST_400));
        }
        assertOutcome(0, 0, 1);
    }

    @Test
    void serverErrorDropsPermit() throws InterruptedException {
        try (HttpClientResponse response = client.get("/server-error").request()) {
            assertThat(response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
        }
        assertOutcome(0, 0, 1);
    }

    @Test
    void escapingBadRequestDropsPermit(Http2TestClient testClient) throws InterruptedException {
        try (Http2TestConnection connection = testClient.createConnection()) {
            connection.completeHandshake(TIMEOUT);
            Http2Headers headers = Http2Headers.create(WritableHeaders.create());
            headers.method(Method.GET);
            headers.path("/bad-request");
            headers.scheme(connection.clientUri().scheme());
            headers.authority(connection.clientUri().authority());
            connection.writer().writeHeaders(headers,
                                             1,
                                             Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM),
                                             FlowControl.Outbound.NOOP);

            assertOutcome(0, 0, 1);
        }
    }

    private void assertOutcome(int successes, int ignored, int dropped) throws InterruptedException {
        assertThat("Request limit token completed", token.completed.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is(true));
        assertThat("Successful permit completions", token.successes.get(), is(successes));
        assertThat("Ignored permit completions", token.ignored.get(), is(ignored));
        assertThat("Dropped permit completions", token.dropped.get(), is(dropped));
    }

    private static final class RecordingLimit implements Limit {
        private volatile RecordingToken token = new RecordingToken();

        @Override
        public Limit copy() {
            return new RecordingLimit();
        }

        @Override
        public String name() {
            return "request-limit";
        }

        @Override
        public String type() {
            return "recording";
        }

        @Override
        public Outcome tryAcquireOutcome(boolean wait) {
            return Outcome.immediateAcceptance(name(), type(), token);
        }
    }

    private static final class RecordingToken implements LimitAlgorithm.Token {
        private final CountDownLatch completed = new CountDownLatch(1);
        private final AtomicInteger successes = new AtomicInteger();
        private final AtomicInteger ignored = new AtomicInteger();
        private final AtomicInteger dropped = new AtomicInteger();

        @Override
        public void success() {
            successes.incrementAndGet();
            completed.countDown();
        }

        @Override
        public void ignore() {
            ignored.incrementAndGet();
            completed.countDown();
        }

        @Override
        public void dropped() {
            dropped.incrementAndGet();
            completed.countDown();
        }
    }
}
