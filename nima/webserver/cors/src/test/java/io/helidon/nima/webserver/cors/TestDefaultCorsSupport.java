/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.nima.webserver.cors;

import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.nima.webclient.api.HttpClientResponse;
import io.helidon.nima.webclient.api.WebClient;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRules;

import org.junit.jupiter.api.Test;

import static io.helidon.common.http.Http.Header.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.noHeader;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Make sure the default CorsSupport behavior is correct (basically, wildcarded sharing).
 */
class TestDefaultCorsSupport {

    static void prepRouting(HttpRules rules, boolean withCors) {
        if (withCors) {
            rules.any(CorsSupport.create()); // Here is where we insert the default CorsSupport.
        }

        rules.get("/greet", (req, res) -> res.send("Hello World!"))
                .options("/greet", (req, res) -> res.status(Http.Status.OK_200).send());
    }

    @Test
    void testGetWithoutCors() {
        WebServer server = null;
        WebClient client;
        try {
            server = WebServer.builder()
                    .routing(it -> prepRouting(it, false))
                    .build()
                    .start();
            client = WebClient.builder()
                    .baseUri("http://localhost:" + server.port())
                    .build();

            try (HttpClientResponse response = client.get("/greet")
                    .request()) {

                String greeting = response.as(String.class);
                assertThat(greeting, is("Hello World!"));
            }
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    @Test
    void testOptionsWithCors() {
        WebServer server = null;
        WebClient client;
        try {
            server = WebServer.builder()
                    .routing(it -> prepRouting(it, true))
                    .build()
                    .start();
            client = WebClient.builder()
                    .baseUri("http://localhost:" + server.port())
                    .build();

            try (HttpClientResponse response = client.get("/greet")
                    .header(Header.ORIGIN, "http://foo.com")
                    .header(Header.HOST, "bar.com")
                    .request()) {

                Headers headers = response.headers();
                assertThat(headers, hasHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*"));
            }
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    @Test
    void testOptionsWithoutCors() {
        WebServer server = null;
        WebClient client;
        try {
            server = WebServer.builder()
                    .routing(it -> prepRouting(it, false))
                    .build()
                    .start();
            client = WebClient.builder()
                    .baseUri("http://localhost:" + server.port())
                    .build();

            try (HttpClientResponse response = client.get("/greet")
                    .header(Header.ORIGIN, "http://foo.com")
                    .header(Header.HOST, "bar.com")
                    .request()) {

                assertThat(response.headers(), noHeader(ACCESS_CONTROL_ALLOW_ORIGIN));
            }
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }
}
