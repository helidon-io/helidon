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

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import io.helidon.common.testing.http.junit5.HttpHeaderMatcher;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@ServerTest
class ContentEncodingNoProviderTest {

    private final Http1Client client;

    ContentEncodingNoProviderTest(Http1Client client) {
        this.client = client;
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        server.contentEncoding(ContentEncodingContext.create());
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/hello", (req, res) -> res.send("hello webserver"))
                .get("/stream", (req, res) -> {
                    try (OutputStream out = res.outputStream()) {
                        out.write("hello webserver".getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }

    @Test
    void testNoProviderIdentityResponseAddsVary() {
        try (Http1ClientResponse response = client.method(Method.GET)
                .uri("/hello")
                .header(HeaderNames.ACCEPT_ENCODING, "identity")
                .request()) {

            assertThat(response.status(), equalTo(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY,
                                                                       HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.entity().as(String.class), equalTo("hello webserver"));
        }
    }

    @Test
    void testNoProviderRejectsIdentityForBufferedResponse() {
        try (Http1ClientResponse response = client.method(Method.GET)
                .uri("/hello")
                .header(HeaderNames.ACCEPT_ENCODING, "identity;q=0")
                .request()) {

            assertThat(response.status(), equalTo(Status.NOT_ACCEPTABLE_406));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY,
                                                                       HeaderNames.ACCEPT_ENCODING_NAME));
        }
    }

    @Test
    void testNoProviderRejectsIdentityForStreamingResponse() {
        try (Http1ClientResponse response = client.method(Method.GET)
                .uri("/stream")
                .header(HeaderNames.ACCEPT_ENCODING, "identity;q=0")
                .request()) {

            assertThat(response.status(), equalTo(Status.NOT_ACCEPTABLE_406));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY,
                                                                       HeaderNames.ACCEPT_ENCODING_NAME));
        }
    }
}
