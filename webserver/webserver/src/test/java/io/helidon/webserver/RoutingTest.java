/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.http.Http;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Demonstrates routing configuration API included in WebServer builder.
 * Demonstrates other outing features
 */
public class RoutingTest {

    /**
     * Use fluent routing API to cover HTTP methods and Paths with handlers.
     */
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

        routing.route(requestStub("/user", Http.Method.POST), responseStub());
        assertThat(checker.handlersInvoked(), is("defaultUserHandler"));

        checker.reset();
        routing.route(requestStub("/user/john", Http.Method.GET), responseStub());
        assertThat(checker.handlersInvoked(), is("namedUserHandler"));
    }

    /**
     * Use routing API to register filter. Filter is just handler which calls {@code request.next()}.
     */
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

        routing.route(requestStub("/admin/user", Http.Method.POST), responseStub());
        assertThat(checker.handlersInvoked(), is("anyPath1,anyPath2,postAdminUser"));

        checker.reset();
        routing.route(requestStub("/admin/user/john", Http.Method.GET), responseStub());
        assertThat(checker.handlersInvoked(), is("anyPath1,anyPath2,getAdminUser"));

        checker.reset();
        routing.route(requestStub("/admin", Http.Method.POST), responseStub());
        assertThat(checker.handlersInvoked(), is("anyPath1,anyPath2,anyAdmin,postAdminAudit"));
    }

    /**
     * Use <i>sub-routers</i> to organize code to services/resources or attach third party filters.
     */
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

        routing.route(requestStub("/user/john", Http.Method.GET), responseStub());
        assertThat(checker.handlersInvoked(), is("getUser"));

        checker.reset();
        routing.route(requestStub("/user", Http.Method.POST), responseStub());
        assertThat(checker.handlersInvoked(), is("createUser"));
    }

    /**
     * Use RequestPredicate fluent API for more advanced routing.
     */
    @Test
    public void filteringExample() {
        assertThrows(RuntimeException.class, () -> {
            Routing.builder()
                    .anyOf(Arrays.asList(Http.Method.PUT, Http.Method.POST), "/foo", RequestPredicate.whenRequest()
                            .containsHeader("my-gr8-header")
                            .thenApply((req, resp) -> {
                                // Some logic.
                            }))
                    .get("/foo", RequestPredicate.whenRequest()
                            .is(req -> isUserAuthenticated(req))
                            .accepts("application/json")
                            .thenApply((req, resp) -> {
                                // Something with authenticated user
                            }))
                    .build();
        });

    }

    public boolean isUserAuthenticated(ServerRequest request) {
        return true;
    }

    private static BareRequest requestStub(String path, Http.Method method) {
        BareRequest bareRequestMock = Mockito.mock(BareRequest.class);
        Mockito.doReturn(URI.create("http://0.0.0.0:1234/" + path)).when(bareRequestMock).uri();
        Mockito.doReturn(method).when(bareRequestMock).method();
        Mockito.doReturn(Mockito.mock(WebServer.class)).when(bareRequestMock).webServer();
        return bareRequestMock;
    }

    private static BareResponse responseStub(){
        BareResponse bareResponseMock = Mockito.mock(BareResponse.class);
        final CompletableFuture<BareResponse> completedFuture =
                CompletableFuture.completedFuture(bareResponseMock);
        Mockito.doReturn(completedFuture).when(bareResponseMock).whenCompleted();
        Mockito.doReturn(completedFuture).when(bareResponseMock).whenHeadersCompleted();
        return bareResponseMock;
    }

    private static final class RoutingChecker {

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
