/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpException;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.ErrorHandler;
import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.testing.junit5.DirectClient;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

@RoutingTest
class ErrorHandlingTest {
    private static final HeaderName CONTROL_HEADER = HeaderNames.create("X-HELIDON-JUNIT");
    private static final Header FIRST = HeaderValues.create(CONTROL_HEADER, "first");
    private static final Header SECOND = HeaderValues.create(CONTROL_HEADER, "second");
    private static final Header ROUTING = HeaderValues.create(CONTROL_HEADER, "routing");
    private static final Header ROUTING_MESSAGE = HeaderValues.create(CONTROL_HEADER, "routing-message");
    private static final Header CUSTOM = HeaderValues.create(CONTROL_HEADER, "custom");
    private static final String ROUTE_EXCEPTION_MESSAGE = "Route exception message should stay server-side";
    private static final String HTTP_EXCEPTION_MESSAGE = "Http exception message should stay server-side";
    private static final String INTERNAL_ERROR_MESSAGE = "Internal Server Error";
    private static final String NOT_FOUND_MESSAGE = "Endpoint not found";

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
                .get("/", ErrorHandlingTest::handler)
                .get("/http-exception", (req, res) -> {
                    throw new HttpException(HTTP_EXCEPTION_MESSAGE, Status.I_AM_A_TEAPOT_418, true);
                });
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
            assertThat(response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
            assertThat(response.entity().as(String.class), is(INTERNAL_ERROR_MESSAGE));
        }
    }

    @Test
    void testUnhandledExceptionMessageNotReturned() {
        try (Http1ClientResponse response = client.get()
                .header(ROUTING_MESSAGE)
                .request()) {
            assertThat(response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
            String entity = response.entity().asOptional(String.class).orElse("");
            assertThat(entity, is(INTERNAL_ERROR_MESSAGE));
            assertThat(entity, not(containsString(ROUTE_EXCEPTION_MESSAGE)));
        }
    }

    @Test
    void testUnhandledHttpExceptionMessageReturned() {
        try (Http1ClientResponse response = client.get("/http-exception")
                .request()) {
            assertThat(response.status(), is(Status.I_AM_A_TEAPOT_418));
            assertThat(response.entity().as(String.class), is(HTTP_EXCEPTION_MESSAGE));
        }
    }

    @Test
    void testNoRouteMessageReturned() {
        try (Http1ClientResponse response = client.get("/missing")
                .request()) {
            assertThat(response.status(), is(Status.NOT_FOUND_404));
            assertThat(response.entity().as(String.class), is(NOT_FOUND_MESSAGE));
        }
    }

    private static void handler(ServerRequest req, ServerResponse res) throws Exception {
        if (req.headers().contains(ROUTING_MESSAGE)) {
            throw new RoutingException(ROUTE_EXCEPTION_MESSAGE);
        }
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
        private RoutingException() {
        }

        private RoutingException(String message) {
            super(message);
        }
    }

    private static class CustomRoutingException extends RoutingException {

    }
}
