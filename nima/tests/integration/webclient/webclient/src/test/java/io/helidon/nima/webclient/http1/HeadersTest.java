/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.webclient.http1;

import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.media.type.ParserMode;
import io.helidon.nima.webclient.ClientResponse;
import io.helidon.nima.webclient.HttpClient;
import io.helidon.nima.webclient.WebClient;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class HeadersTest {

    private static WebServer server;

    @BeforeAll
    static void beforeAll() {
        server = WebServer.builder()
                .routing(routing -> routing.register("/test", new TestService()).build())
                .start();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    @Test
    public void testInvalidContentType() {
        HttpClient<Http1ClientRequest, Http1ClientResponse> client = WebClient.builder()
                .baseUri("http://localhost:" + server.port() + "/test")
                .build();
        try (ClientResponse res = client.method(Http.Method.GET)
                .path("/invalidContentType")
                .request()) {
            Headers h = res.headers();
            Http.HeaderValue contentType = h.get(Http.Header.CONTENT_TYPE);
            assertThat(res.status(), is(Http.Status.OK_200));
            assertThat(contentType.value(), is(TestService.INVALID_CONTENT_TYPE_VALUE));
        }
    }

    @Test
    public void testInvalidTextContentTypeStrict() {
        HttpClient<Http1ClientRequest, Http1ClientResponse> client = WebClient.builder()
                .baseUri("http://localhost:" + server.port() + "/test")
                .build();
        ClientResponse res = client.method(Http.Method.GET)
                .path("/invalidTextContentType")
                .request();
        Headers h = res.headers();
        Http.HeaderValue contentType = h.get(Http.Header.CONTENT_TYPE);
        assertThat(res.status(), is(Http.Status.OK_200));
        assertThat(contentType.value(), is(TestService.INVALID_CONTENT_TYPE_TEXT));
    }

    @Test
    public void testInvalidTextContentTypeRelaxed() {
        HttpClient<Http1ClientRequest, Http1ClientResponse> client = WebClient.builder()
                .baseUri("http://localhost:" + server.port() + "/test")
                .mediaTypeParserMode(ParserMode.RELAXED)
                .build();
        ClientResponse res = client.method(Http.Method.GET)
                .path("/invalidTextContentType")
                .request();
        Headers h = res.headers();
        Http.HeaderValue contentType = h.get(Http.Header.CONTENT_TYPE);
        assertThat(res.status(), is(Http.Status.OK_200));
        assertThat(contentType.value(), is(TestService.RELAXED_CONTENT_TYPE_TEXT));
    }

    static final class TestService implements HttpService {

        TestService() {
        }

        @Override
        public void routing(HttpRules rules) {
            rules
                    .get("/invalidContentType", this::invalidContentType)
                    .get("/invalidTextContentType", this::invalidTextContentType);
        }

        private static final String INVALID_CONTENT_TYPE_VALUE = "invalid header value";

        private void invalidContentType(ServerRequest request, ServerResponse response) {
            response.header(Http.Header.CONTENT_TYPE, INVALID_CONTENT_TYPE_VALUE)
                    .send();
        }

        private static final String INVALID_CONTENT_TYPE_TEXT = "text";
        private static final String RELAXED_CONTENT_TYPE_TEXT = "text/plain";

        // Returns Content-Type: text instead of text/plain
        private void invalidTextContentType(ServerRequest request, ServerResponse response) {
            response.header(Http.Header.CONTENT_TYPE, INVALID_CONTENT_TYPE_TEXT)
                    .send();
        }

    }

}
