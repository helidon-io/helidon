/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

import io.helidon.common.http.Http;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Demonstrates routing configuration API included in WebServer builder.
 * Demonstrates other outing features
 */
@Disabled("Routing is in incremental implementation tryProcess!")
public class RoutingTest {

    /**
     * Use fluent routing API to cover HTTP methods and Paths with handlers.
     */
    @Test
    public void basicRouting() throws Exception {
        assertThrows(RuntimeException.class, () -> {
            Routing.builder()
                     .post("/user", (req, resp) -> {
                         // Do something GR8 with response.
                     })
                     .get("/user/{name}", (req, resp) -> {
                         // Do something GR8 with response.
                     })
                     .build();
        });
    }

    /**
     * Use routing API to register filter. Filter is just handler which calls {@code request.next()}.
     */
    @Test
    public void routeFilters() throws Exception {
        assertThrows(RuntimeException.class, () -> {
            Routing.builder()
                     .any((req, resp) -> { // Any http request, any path
                         // Transform request cookie to session
                         req.next();
                     }, (req, resp) -> { // More filters/handlers can be registered in one method, just for convenience
                         // Another filtering logic
                         req.next();
                     })
                     .any("/admin/", (req, resp) -> {
                         // If not authorize admin throw RuntimeException or response something, else
                         req.next();
                     })
                     .post("/admin", (req, resp) -> {
                         // Write audit record about modification request
                         req.next();
                     })
                     .post("/admin/user", (req, resp) -> {
                         // Do something GR8 with response.
                     })
                     .get("/admin/user/{name}", (req, resp) -> {
                         // Do something GR8 with response.
                     })
                     .build();
        });
    }

    /**
     * Use <i>sub-routers</i> to organize code to services/resources or attach third party filters.
     */
    @Test
    public void subRouting() throws Exception {
        assertThrows(RuntimeException.class, () -> {
            Routing.builder()
                     .register("/user", new UserService())
                     .build();
        });
    }

    /**
     * Use Selector fluent API for more advanced routing.
     */
    @Test
    public void filteringExample() throws Exception {
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

    static class UserService implements Service {

        @Override
        public void update(Routing.Rules routingRules) {
            routingRules.get("{userName}", this::getUser)
                        .post(this::createUser);
        }

        public void createUser(ServerRequest request, ServerResponse response) {
        }

        public void getUser(ServerRequest request, ServerResponse response) {
        }
    }
}
