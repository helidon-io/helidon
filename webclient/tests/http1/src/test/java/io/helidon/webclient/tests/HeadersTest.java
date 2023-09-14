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

package io.helidon.webclient.tests;

import java.util.Optional;

import io.helidon.common.media.type.ParserMode;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.HttpMediaType;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;

@ServerTest
class HeadersTest {

    private final WebClient client;

    HeadersTest(WebClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.register("/test", new TestService());
    }


    // Verify that invalid content type is present in response headers and is accessible
    @Test
    public void testInvalidContentType() {
        try (HttpClientResponse res = client.method(Method.GET)
                .path("/test/invalidContentType")
                .request()) {
            ClientResponseHeaders h = res.headers();
            Header contentType = h.get(HeaderNames.CONTENT_TYPE);
            assertThat(res.status(), is(Status.OK_200));
            assertThat(contentType.value(), is(TestService.INVALID_CONTENT_TYPE_VALUE));
        }
    }

    // Verify that "Content-Type: text" header parsing fails in strict mode
    @Test
    public void testInvalidTextContentTypeStrict() {
        try (HttpClientResponse res = client.method(Method.GET)
                .path("/test/invalidTextContentType")
                .request()) {
            assertThat(res.status(), is(Status.OK_200));
            Headers h = res.headers();
            // Raw protocol data value
            Header rawContentType = h.get(HeaderNames.CONTENT_TYPE);
            assertThat(rawContentType.value(), is(TestService.INVALID_CONTENT_TYPE_TEXT));
            // Media type parsed value is invalid, IllegalArgumentException shall be thrown
            try {
                Optional<HttpMediaType> httpMediaType = h.contentType();
                fail("Content-Type: text parsing must throw an exception in strict mode, got: " + httpMediaType);
            } catch (IllegalArgumentException ex) {
                assertThat(ex.getMessage(), is("Cannot parse media type: text"));
            }
        }
    }

    // Verify that "Content-Type: text" header parsing returns text/plain in relaxed mode
    @Test
    public void testInvalidTextContentTypeRelaxed() {
        WebClient client = WebClient.builder()
                .from(this.client.prototype())
                .mediaTypeParserMode(ParserMode.RELAXED)
                .build();
        try (HttpClientResponse res = client.method(Method.GET)
                .path("/test/invalidTextContentType")
                .request()) {
            assertThat(res.status(), is(Status.OK_200));
            Headers h = res.headers();
            // Raw protocol data value
            Header rawContentType = h.get(HeaderNames.CONTENT_TYPE);
            assertThat(rawContentType.value(), is(TestService.INVALID_CONTENT_TYPE_TEXT));
            // Media type parsed value
            Optional<HttpMediaType> contentType = h.contentType();
            assertThat(contentType.isPresent(), is(true));
            assertThat(contentType.get().text(), is(TestService.RELAXED_CONTENT_TYPE_TEXT));
        }
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
            response.header(HeaderNames.CONTENT_TYPE, INVALID_CONTENT_TYPE_VALUE)
                    .send();
        }

        private static final String INVALID_CONTENT_TYPE_TEXT = "text";
        private static final String RELAXED_CONTENT_TYPE_TEXT = "text/plain";

        // Returns Content-Type: text instead of text/plain
        private void invalidTextContentType(ServerRequest request, ServerResponse response) {
            response.header(HeaderNames.CONTENT_TYPE, INVALID_CONTENT_TYPE_TEXT)
                    .send();
        }

    }

}
