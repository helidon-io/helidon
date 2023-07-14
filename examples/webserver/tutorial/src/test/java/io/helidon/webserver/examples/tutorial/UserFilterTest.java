/*
 * Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.examples.tutorial;

import io.helidon.common.http.Http;
import io.helidon.nima.testing.junit5.webserver.DirectClient;
import io.helidon.nima.testing.junit5.webserver.RoutingTest;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.http.HttpRouting;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link UserFilter}.
 */
@RoutingTest
public class UserFilterTest {

    private final DirectClient client;

    UserFilterTest(DirectClient client) {
        this.client = client;
    }

    @BeforeAll
    static void setup(HttpRouting.Builder routing) {
        routing.any(new UserFilter())
               .any((req, res) -> res.send(req.context()
                                              .get(User.class)
                                              .orElse(User.ANONYMOUS)
                                              .alias()));
    }

    @Test
    public void filter() {
        try (Http1ClientResponse response = client.get().request()) {
            assertThat(response.entity().as(String.class), is("anonymous"));
        }

        try (Http1ClientResponse response = client.get()
                                                  .header(Http.Header.COOKIE, "Unauthenticated-User-Alias=Foo")
                                                  .request()) {
            assertThat(response.entity().as(String.class), is("Foo"));
        }
    }
}
