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

import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

abstract class MatchingPatternBase {
    static final Handler HANDLER = (req, res) -> res.send(req.matchingPattern().orElse(""));

    private final Http1Client client;

    MatchingPatternBase(Http1Client client) {
        this.client = client;
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/foo", "/foo/bar", "/foo/bar/baz"})
    void testBasic(String value) {
        try (Http1ClientResponse response = client.get(value).request()) {
            assertThat(response.status(), is(Status.OK_200));
            String message = response.as(String.class);
            assertThat(message, is(value));
        }
    }

    @Test
    void testParams() {
        try (Http1ClientResponse response = client.get("/greet/john").request()) {
            assertThat(response.status(), is(Status.OK_200));
            String message = response.as(String.class);
            assertThat(message, is("/greet/{name}"));
        }
        try (Http1ClientResponse response = client.get("/greet/john/doe").request()) {
            assertThat(response.status(), is(Status.OK_200));
            String message = response.as(String.class);
            assertThat(message, is("/greet/{name1}/{}"));
        }
        try (Http1ClientResponse response = client.get("/greet/john/and/doe").request()) {
            assertThat(response.status(), is(Status.OK_200));
            String message = response.as(String.class);
            assertThat(message, is("/greet/{name1}/and/{name2}"));
        }
    }

    @Test
    void testWildcard() {
        try (Http1ClientResponse response = client.get("/greet1/john").request()) {
            assertThat(response.status(), is(Status.OK_200));
            String message = response.as(String.class);
            assertThat(message, is("/greet1/*"));
        }
        try (Http1ClientResponse response = client.get("/greet2/greet/john").request()) {
            assertThat(response.status(), is(Status.OK_200));
            String message = response.as(String.class);
            assertThat(message, is("/greet2/greet/*"));
        }
        try (Http1ClientResponse response = client.get("/greet4/greet/john").request()) {
            assertThat(response.status(), is(Status.OK_200));
            String message = response.as(String.class);
            assertThat(message, is("/greet4/*/*"));
        }
    }

    @Test
    void testOptional() {
        try (Http1ClientResponse response = client.get("/greet3").request()) {
            assertThat(response.status(), is(Status.OK_200));
            String message = response.as(String.class);
            assertThat(message, is("/greet3[/greet]"));
        }
        try (Http1ClientResponse response = client.get("/greet3/greet").request()) {
            assertThat(response.status(), is(Status.OK_200));
            String message = response.as(String.class);
            assertThat(message, is("/greet3[/greet]"));
        }
    }

    @Test
    void testService() {
        try (Http1ClientResponse response = client.get("/greet-service/john").request()) {
            assertThat(response.status(), is(Status.OK_200));
            String message = response.as(String.class);
            assertThat(message, is("/greet-service/{name}"));
        }
        try (Http1ClientResponse response = client.get("/greet-service/john/doe").request()) {
            assertThat(response.status(), is(Status.OK_200));
            String message = response.as(String.class);
            assertThat(message, is("/greet-service/{name1}/{name2}"));
        }
        try (Http1ClientResponse response = client.get("/greet-service/john/and/doe").request()) {
            assertThat(response.status(), is(Status.OK_200));
            String message = response.as(String.class);
            assertThat(message, is("/greet-service/{name1}/and/{name2}"));
        }
    }

    static class GreetService implements HttpService {

        @Override
        public void routing(HttpRules rules) {
            rules.get("/", HANDLER)
                    .get("/{name}", HANDLER)
                    .get("/{name1}/{name2}", HANDLER)
                    .get("/{name1}/and/{name2}", HANDLER);
        }
    }
}
