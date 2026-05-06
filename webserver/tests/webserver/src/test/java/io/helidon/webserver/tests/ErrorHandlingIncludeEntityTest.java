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

import io.helidon.http.HttpException;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.ErrorHandling;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class ErrorHandlingIncludeEntityTest {
    private static final String ROUTE_EXCEPTION_MESSAGE = "Configured route exception message";
    private static final String HTTP_EXCEPTION_MESSAGE = "Configured HTTP exception message";
    private static final String NOT_FOUND_MESSAGE = "Endpoint not found";

    private final Http1Client client;

    ErrorHandlingIncludeEntityTest(Http1Client client) {
        this.client = client;
    }

    @SetUpServer
    static void setupServer(WebServerConfig.Builder builder) {
        builder.errorHandling(ErrorHandling.builder()
                                      .includeEntity(true)
                                      .build());
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/", (req, res) -> {
            throw new RoutingException(ROUTE_EXCEPTION_MESSAGE);
        });
        rules.get("/http-exception", (req, res) -> {
            throw new HttpException(HTTP_EXCEPTION_MESSAGE, Status.I_AM_A_TEAPOT_418, true);
        });
    }

    @Test
    void testUnhandledExceptionMessageReturnedWhenConfigured() {
        try (Http1ClientResponse response = client.get()
                .request()) {
            assertThat(response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
            assertThat(response.entity().as(String.class), is(ROUTE_EXCEPTION_MESSAGE));
        }
    }

    @Test
    void testUnhandledHttpExceptionMessageReturnedWhenConfigured() {
        try (Http1ClientResponse response = client.get("/http-exception")
                .request()) {
            assertThat(response.status(), is(Status.I_AM_A_TEAPOT_418));
            assertThat(response.entity().as(String.class), is(HTTP_EXCEPTION_MESSAGE));
        }
    }

    @Test
    void testNoRouteMessageReturnedWhenConfigured() {
        try (Http1ClientResponse response = client.get("/missing")
                .request()) {
            assertThat(response.status(), is(Status.NOT_FOUND_404));
            assertThat(response.entity().as(String.class), is(NOT_FOUND_MESSAGE));
        }
    }

    private static class RoutingException extends Exception {
        private RoutingException(String message) {
            super(message);
        }
    }
}
