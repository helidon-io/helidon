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

package io.helidon.webserver.tests.sse;

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class SseBeforeSendTest {
    private static final String BEFORE_SEND_CALLS = "Before-Send-Calls";

    private final Http1Client client;

    SseBeforeSendTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/sseBeforeSendStatus", (req, res) -> {
            res.beforeSend(() -> res.status(Status.BAD_REQUEST_400));
            SseBaseTest.sseString1(req, res);
        });
        rules.get("/sseBeforeSendHeader", (req, res) -> {
            AtomicInteger beforeSendCalls = new AtomicInteger();
            res.beforeSend(() -> res.header(BEFORE_SEND_CALLS,
                                            Integer.toString(beforeSendCalls.incrementAndGet())));
            SseBaseTest.sseString1(req, res);
        });
    }

    @Test
    void testBeforeSendStatusIsAppliedToSseWithoutAltSvc() {
        try (Http1ClientResponse response = client.get("/sseBeforeSendStatus").request()) {
            assertThat(response.status(), is(Status.BAD_REQUEST_400));
        }
    }

    @Test
    void testBeforeSendHeaderIsAppliedOnceToSseWithoutAltSvc() {
        try (Http1ClientResponse response = client.get("/sseBeforeSendHeader").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers().first(HeaderNames.create(BEFORE_SEND_CALLS)).orElse(null), is("1"));
        }
    }
}
