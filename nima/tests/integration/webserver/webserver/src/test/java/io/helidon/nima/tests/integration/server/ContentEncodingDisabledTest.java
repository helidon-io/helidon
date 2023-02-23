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
package io.helidon.nima.tests.integration.server;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.nima.http.encoding.ContentDecoder;
import io.helidon.nima.http.encoding.ContentEncoder;
import io.helidon.nima.http.encoding.ContentEncodingContext;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.nima.webserver.http1.DefaultHttp1Config;
import io.helidon.nima.webserver.http1.Http1ConnectionProvider;
import io.helidon.nima.webserver.spi.ServerConnectionProvider;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Verify that server responds with status 400 - Bad Request when:
 * <ul>
 *     <li>Content encoding is completely disabled using custom context which does not contain even
 *         default "dentity" encoder</li>
 *     <li>Request contains Content-Encoding header and also something to trigger EntityStyle.NONE
 *         replacement, e.g it's a POST request with Content-Length > 0</li>
 * </ul>
 */
@ServerTest
class ContentEncodingDisabledTest {

    private final Http1Client client;

    ContentEncodingDisabledTest(Http1Client client) {
        this.client = client;
    }

    @SetUpServer
    static void server(WebServer.Builder server) {
        ServerConnectionProvider http1 = Http1ConnectionProvider.builder()
                .http1Config(DefaultHttp1Config.builder().build())
                .build();
        server.addConnectionProvider(http1);
        // Content encoding needs to be completely disabled
        server.contentEncodingContext(new EmptyEncodingContext());
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.any(ContentEncodingDisabledTest::handleRequest);
    }

    @Test
    void testContentEncodingHeader() {
        try (Http1ClientResponse response = client.method(Http.Method.POST)
                        .header(Http.Header.CONTENT_ENCODING, "data")
                        .submit("any")) {
            assertThat(response.status(), is(Http.Status.BAD_REQUEST_400));
            assertThat(response.as(String.class), is("Content-Encoding header present when content encoding is disabled"));
        }
    }

    // Just a placeholder, route call in Http1Connection shall throw an exception so this is never called
    private static void handleRequest(ServerRequest request, ServerResponse response) {
        response.send();
    }

    // Completely disable encoding. Even default "identity" encoding shall not be present.
    static final class EmptyEncodingContext implements ContentEncodingContext {

        private EmptyEncodingContext() {
        }

        @Override
        public boolean contentEncodingEnabled() {
            return false;
        }

        @Override
        public boolean contentDecodingEnabled() {
            return false;
        }

        @Override
        public boolean contentEncodingSupported(String encodingId) {
            return false;
        }

        @Override
        public boolean contentDecodingSupported(String encodingId) {
            return false;
        }

        @Override
        public ContentEncoder encoder(String encodingId) throws NoSuchElementException {
            return null;
        }

        @Override
        public ContentDecoder decoder(String encodingId) throws NoSuchElementException {
            return null;
        }

        @Override
        public ContentEncoder encoder(Headers headers) {
            return null;
        }

    }

}
