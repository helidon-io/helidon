/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.http.ContextualRegistry;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link Routing#route(BareRequest, BareResponse)}.
 */
public class RoutingTest {

    @Test
    public void basicRouting() {
        final RoutingChecker checker = new RoutingChecker();
        Routing routing = Routing.builder()
                .post("/user", (req, resp) -> {
                    checker.handlerInvoked("defaultUserHandler");
                })
                .get("/user/{name}", (req, resp) -> {
                    checker.handlerInvoked("namedUserHandler");
                })
                .build();

        routing.route(mockRequest("/user", Http.Method.POST), mockResponse());
        assertThat(checker.handlersInvoked(), is("defaultUserHandler"));

        checker.reset();
        routing.route(mockRequest("/user/john", Http.Method.GET), mockResponse());
        assertThat(checker.handlersInvoked(), is("namedUserHandler"));
    }

    @Test
    public void routeFilters() {
        final RoutingChecker checker = new RoutingChecker();
        Routing routing = Routing.builder()
                .any((req, resp) -> {
                    checker.handlerInvoked("anyPath1");
                    req.next();
                }, (req, resp) -> {
                    checker.handlerInvoked("anyPath2");
                    req.next();
                })
                .any("/admin", (req, resp) -> {
                    checker.handlerInvoked("anyAdmin");
                    req.next();
                })
                .post("/admin", (req, resp) -> {
                    checker.handlerInvoked("postAdminAudit");
                    req.next();
                })
                .post("/admin/user", (req, resp) -> {
                    checker.handlerInvoked("postAdminUser");
                })
                .get("/admin/user/{name}", (req, resp) -> {
                    checker.handlerInvoked("getAdminUser");
                })
                .build();

        routing.route(mockRequest("/admin/user", Http.Method.POST), mockResponse());
        assertThat(checker.handlersInvoked(), is("anyPath1,anyPath2,postAdminUser"));

        checker.reset();
        routing.route(mockRequest("/admin/user/john", Http.Method.GET), mockResponse());
        assertThat(checker.handlersInvoked(), is("anyPath1,anyPath2,getAdminUser"));

        checker.reset();

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> routing.route(mockRequest("/admin", Http.Method.POST),
                                                                      mockResponse()));

        assertThat(e.getMessage(), is("Transformation failed!"));

        assertThat(checker.handlersInvoked(), is("anyPath1,anyPath2,anyAdmin,postAdminAudit"));
    }

    @Test
    public void subRouting() {
        final RoutingChecker checker = new RoutingChecker();
        Routing routing = Routing.builder()
                .register("/user", (rules) -> {
                    rules.get("/{name}", (req, res) -> {
                        checker.handlerInvoked("getUser");
                    }).post((req, res) -> {
                        checker.handlerInvoked("createUser");
                    });
                }).build();

        routing.route(mockRequest("/user/john", Http.Method.GET), mockResponse());
        assertThat(checker.handlersInvoked(), is("getUser"));

        checker.reset();
        routing.route(mockRequest("/user", Http.Method.POST), mockResponse());
        assertThat(checker.handlersInvoked(), is("createUser"));
    }

    static BareRequest mockRequest(String path, Http.Method method) {
        BareRequest bareRequestMock = mock(BareRequest.class);
        doReturn(URI.create("http://0.0.0.0:1234/" + path)).when(bareRequestMock).uri();
        doReturn(method).when(bareRequestMock).method();
        doReturn(Single.empty()).when(bareRequestMock).bodyPublisher();
        WebServer webServerMock = mock(WebServer.class);
        when(webServerMock.context()).thenReturn(ContextualRegistry.create());
        doReturn(webServerMock).when(bareRequestMock).webServer();
        return bareRequestMock;
    }

    static BareResponse mockResponse(){
        BareResponse bareResponseMock = Mockito.mock(BareResponse.class);
        final CompletableFuture<BareResponse> completedFuture =
                CompletableFuture.completedFuture(bareResponseMock);
        Mockito.doReturn(Single.from(completedFuture)).when(bareResponseMock).whenCompleted();
        Mockito.doReturn(Single.from(completedFuture)).when(bareResponseMock).whenHeadersCompleted();
        return bareResponseMock;
    }

    static final class RoutingChecker {

        String str = "";

        public void handlerInvoked(String id){
            str += str.isEmpty() ? id : "," + id;
        }

        public void reset(){
            str = "";
        }

        public String handlersInvoked(){
            return str;
        }
    }
}
