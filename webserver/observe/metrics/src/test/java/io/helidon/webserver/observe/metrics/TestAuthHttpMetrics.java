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

package io.helidon.webserver.observe.metrics;

import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientRequest;
import io.helidon.webserver.Route;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@ServerTest
class TestAuthHttpMetrics {

    private final Http1Client client;

    TestAuthHttpMetrics(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void setup(HttpRouting.Builder builder) {
        builder.get("/greet", (req, resp) -> resp.send("Hello, World!"));
    }

    @Test
    void testAuthHttpMetrics() {
        int beforeInvocation = TestAutoHttpMetricsProvider.counter();

        try (var resp = client.get("/greet").request()) {
            assertThat("Response status", resp.status().code(), is(equalTo(200)));
            assertThat("Filter invocation count delta",
                       TestAutoHttpMetricsProvider.counter() - beforeInvocation,
                       is(1));
        }
    }
}
