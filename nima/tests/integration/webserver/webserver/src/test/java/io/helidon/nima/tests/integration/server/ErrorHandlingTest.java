/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
import io.helidon.nima.testing.junit5.webserver.DirectClient;
import io.helidon.nima.testing.junit5.webserver.RoutingTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.http.ErrorHandler;
import io.helidon.nima.webserver.http.Filter;
import io.helidon.nima.webserver.http.FilterChain;
import io.helidon.nima.webserver.http.HttpRouting;
import io.helidon.nima.webserver.http.RoutingRequest;
import io.helidon.nima.webserver.http.RoutingResponse;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RoutingTest
class ErrorHandlingTest {
    private static final Http.HeaderName CONTROL_HEADER = Http.HeaderNames.create("X-HELIDON-JUNIT");
    private static final Http.Header FIRST = Http.Headers.create(CONTROL_HEADER, "first");
    private static final Http.Header SECOND = Http.Headers.create(CONTROL_HEADER, "second");
    private static final Http.Header ROUTING = Http.Headers.create(CONTROL_HEADER, "routing");
    private static final Http.Header CUSTOM = Http.Headers.create(CONTROL_HEADER, "custom");

    private final Http1Client client;

    ErrorHandlingTest(DirectClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        builder.error(FirstException.class, new FirstHandler())
                .error(SecondException.class, new SecondHandler())
                .error(CustomRoutingException.class, new CustomRoutingHandler())
                .addFilter(new FirstFilter())
                .addFilter(new SecondFilter())
                .get("/", ErrorHandlingTest::handler);
    }

    @Test
    void testOk() {
        String response = client.get()
                .requestEntity(String.class);
        assertThat(response, is("Done"));
    }

    @Test
    void testFirst() {
        String response = client.get()
                .header(FIRST)
                .requestEntity(String.class);
        assertThat(response, is("First"));
    }

    @Test
    void testSecond() {
        String response = client.get()
                .header(SECOND)
                .requestEntity(String.class);
        assertThat(response, is("Second"));
    }

    @Test
    void testCustom() {
        String response = client.get()
                .header(CUSTOM)
                .requestEntity(String.class);
        assertThat(response, is("Custom"));
    }

    @Test
    void testUnhandled() {
        try (Http1ClientResponse response = client.get()
                .header(ROUTING)
                .request()) {
            assertThat(response.status(), is(Http.Status.INTERNAL_SERVER_ERROR_500));
            assertThat(response.headers(), hasHeader(Http.Headers.CONTENT_LENGTH_ZERO));
        }
    }

    private static void handler(ServerRequest req, ServerResponse res) throws Exception {
        if (req.headers().contains(ROUTING)) {
            throw new RoutingException();
        }
        if (req.headers().contains(CUSTOM)) {
            throw new CustomRoutingException();
        }
        res.send("Done");
    }

    private static class FirstFilter implements Filter {
        @Override
        public void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {
            if (req.headers().contains(FIRST)) {
                throw new FirstException();
            }
            chain.proceed();
        }
    }

    private static class SecondFilter implements Filter {
        @Override
        public void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {
            if (req.headers().contains(SECOND)) {
                throw new SecondException();
            }
            chain.proceed();
        }
    }

    private static class FirstHandler implements ErrorHandler<FirstException> {
        @Override
        public void handle(ServerRequest req, ServerResponse res, FirstException throwable) {
            res.send("First");
        }
    }

    private static class SecondHandler implements ErrorHandler<SecondException> {
        @Override
        public void handle(ServerRequest req, ServerResponse res, SecondException throwable) {
            res.send("Second");
        }
    }

    private static class CustomRoutingHandler implements ErrorHandler<CustomRoutingException> {
        @Override
        public void handle(ServerRequest req, ServerResponse res, CustomRoutingException throwable) {
            res.send("Custom");
        }
    }

    private static class FirstException extends RuntimeException {
    }

    private static class SecondException extends RuntimeException {
    }

    private static class RoutingException extends Exception {

    }

    private static class CustomRoutingException extends RoutingException {

    }
}
