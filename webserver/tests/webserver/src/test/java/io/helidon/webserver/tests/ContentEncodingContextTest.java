/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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
import java.util.NoSuchElementException;

import io.helidon.common.testing.http.junit5.HttpHeaderMatcher;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.encoding.ContentEncoder;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.encoding.ContentEncodingContextConfig;
import io.helidon.http.encoding.gzip.GzipEncoding;
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
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@ServerTest
class ContentEncodingContextTest {

    private static final Header VARY_ACCEPT_ENCODING =
            HeaderValues.createCached(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME);
    private static final Header VARY_ORIGIN =
            HeaderValues.createCached(HeaderNames.VARY, HeaderNames.ORIGIN.defaultCase());
    private static final CustomizedEncodingContext encodingContext = new CustomizedEncodingContext();

    private final Http1Client client;

    ContentEncodingContextTest(Http1Client socketHttpClient) {
        this.client = socketHttpClient;
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        server.contentEncoding(encodingContext);
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/hello", (_, res) -> res.send("hello webserver"))
                .get("/stream", (_, res) -> {
                    try (OutputStream out = res.outputStream()) {
                        out.write("hello webserver".getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .get("/vary", (_, res) -> {
                    res.headers().add(HeaderValues.create(HeaderNames.VARY, HeaderNames.ORIGIN.defaultCase()));
                    res.send("hello webserver");
                });
    }

    @Test
    void testAutomaticContentEncodingAddsVaryWithoutRequestHeader() {
        try (Http1ClientResponse response = client.method(Method.GET).uri("/hello").request()) {
            assertThat(response.status(), equalTo(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY,
                                                                       HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.entity().as(String.class), equalTo("hello webserver"));
            assertThat(encodingContext.NO_ACCEPT_ENCODING_COUNT, greaterThan(0));
        }
    }

    @Test
    void testAutomaticContentEncodingAddsVaryWithoutRequestHeaderForOutputStream() {
        try (Http1ClientResponse response = client.method(Method.GET).uri("/stream").request()) {
            assertThat(response.status(), equalTo(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY,
                                                                       HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.entity().as(String.class), equalTo("hello webserver"));
        }
    }

    @Test
    void testAutomaticContentEncodingAddsVaryAcceptEncoding() {
        try (Http1ClientResponse response = client.method(Method.GET)
                .uri("/hello")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip")
                .request()) {

            assertThat(response.status(), equalTo(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY,
                                                                       HeaderNames.ACCEPT_ENCODING_NAME));
        }
    }

    @Test
    void testAutomaticContentEncodingAddsVaryAcceptEncodingForOutputStream() {
        try (Http1ClientResponse response = client.method(Method.GET)
                .uri("/stream")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip")
                .request()) {

            assertThat(response.status(), equalTo(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY,
                                                                       HeaderNames.ACCEPT_ENCODING_NAME));
        }
    }

    @Test
    void testAutomaticContentEncodingPreservesVary() {
        try (Http1ClientResponse response = client.method(Method.GET).uri("/vary").request()) {
            assertThat(response.headers().values(HeaderNames.VARY), hasSize(2));
            assertThat(response.headers().containsToken(VARY_ORIGIN), is(true));
            assertThat(response.headers().containsToken(VARY_ACCEPT_ENCODING), is(true));
        }
    }

    private static class CustomizedEncodingContext implements ContentEncodingContext {
        int ACCEPT_ENCODING_COUNT = 0;

        int NO_ACCEPT_ENCODING_COUNT = 0;

        ContentEncodingContext contentEncodingContext = ContentEncodingContext.builder()
                .addContentEncoding(GzipEncoding.create())
                .build();

        @Override
        public ContentEncodingContextConfig prototype() {
            return contentEncodingContext.prototype();
        }

        @Override
        public boolean contentEncodingEnabled() {
            return true;
        }

        @Override
        public boolean contentDecodingEnabled() {
            return contentEncodingContext.contentDecodingEnabled();
        }

        @Override
        public boolean contentEncodingSupported(String encodingId) {
            return contentEncodingContext.contentEncodingSupported(encodingId);
        }

        @Override
        public boolean contentDecodingSupported(String encodingId) {
            return contentEncodingContext.contentDecodingSupported(encodingId);
        }

        @Override
        public ContentEncoder encoder(String encodingId) throws NoSuchElementException {
            return contentEncodingContext.encoder(encodingId);
        }

        @Override
        public ContentDecoder decoder(String encodingId) throws NoSuchElementException {
            return contentEncodingContext.decoder(encodingId);
        }

        @Override
        public ContentEncoder encoder(Headers headers) {
            if (headers.contains(HeaderNames.ACCEPT_ENCODING)) {
                ACCEPT_ENCODING_COUNT++;
            } else {
                NO_ACCEPT_ENCODING_COUNT++;
            }
            return contentEncodingContext.encoder(headers);
        }

    }
}
