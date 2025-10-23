/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import java.time.Duration;

import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.http1.Http1Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class BadFilterTest {
    private final Http1Client client;

    BadFilterTest(Http1Client client) {
        this.client = client;
    }


    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        builder.addFilter(new BadFilter())
                .route(Http1Route.route(Method.GET,
                                        "/",
                                        (req, res) -> res.send("Hello!")));
    }

    @Test
    void testRequest() {
        var response = client.method(Method.GET)
                .readTimeout(Duration.ofSeconds(1))
                .request(String.class);

        assertThat(response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
    }

    private static class BadFilter implements Filter {
        @Override
        public void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {
            // forgot to call chain.proceed()
            System.out.println();
        }
    }
}
