/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import io.helidon.common.Weighted;
import io.helidon.http.BadRequestException;
import io.helidon.http.Header;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.http1.Http1Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class BadHostTest {
    private static final Header BAD_HOST_HEADER = HeaderValues.create("Host", "localhost:808a");
    private static final Header GOOD_HOST_HEADER = HeaderValues.create("Host", "fixed:8080");

    private final Http1Client client;

    BadHostTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        builder.route(Http1Route.route(Method.GET,
                                       "/",
                                       (req, res) -> res.send(req.requestedUri().host())))
//                .error(NumberFormatException.class, (req, res, ex) -> {
//                    res.status(Status.BAD_REQUEST_400)
//                            .send("Number format exception");
//                })
                .addFilter(new TestFilterGetAuthority())
                .addFeature(new CheckHostHeaderFeature());
    }

    @SetUpServer
    static void setUpServer(WebServerConfig.Builder builder) {
        //        builder.directHandlers(DirectHandlers.builder()
        //                                       .addHandler(DirectHandler.EventType.INT, BadRequestTest::badRequestHandler)
        //                                       .build());
    }

    @Test
    void testOk() {
        String response = client.method(Method.GET)
                .requestEntity(String.class);

        assertThat(response, is("localhost"));
    }

    @Test
    void testInvalidRequest() {
        ClientResponseTyped<String> response = client.method(Method.GET)
                .header(BAD_HOST_HEADER)
                .request(String.class);

        assertThat(response.status(), is(Status.BAD_REQUEST_400));
        assertThat(response.entity(), is("Number format exception"));
    }

    static class CheckHostHeaderFeature implements HttpFeature, Weighted {
        @Override
        public void setup(HttpRouting.Builder routing) {
            routing.addFilter((chain, req, res) -> {
                try {
                    req.requestedUri();
                    chain.proceed();
                } catch (Exception e) {
                    throw new BadRequestException("Invalid host header", e);
                }
            });
        }

        @Override
        public double weight() {
            return 1000;
        }
    }

    private static class TestFilterGetAuthority implements Filter, Weighted {
        @Override
        public void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {
            req.requestedUri();
            chain.proceed();
        }

        @Override
        public double weight() {
            return 100;
        }
    }

    //
    //    private static DirectHandler.TransportResponse badRequestHandler(DirectHandler.TransportRequest request,
    //                                                                     DirectHandler.EventType eventType,
    //                                                                     Status httpStatus,
    //                                                                     ServerResponseHeaders responseHeaders,
    //                                                                     String message) {
    //        if (request.path().equals("/redirect")) {
    //            return DirectHandler.TransportResponse.builder()
    //                    .status(Status.TEMPORARY_REDIRECT_307)
    //                    .header(HeaderNames.LOCATION, "/errorPage")
    //                    .build();
    //        }
    //        return DirectHandler.TransportResponse.builder()
    //                .status(Status.create(Status.BAD_REQUEST_400.code(),
    //                                      CUSTOM_REASON_PHRASE))
    //                .headers(responseHeaders)
    //                .entity(CUSTOM_ENTITY)
    //                .build();
    //    }
}
