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

package io.helidon.webserver.tests.http2;

import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.encoding.gzip.GzipEncoding;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeaderValue;
import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.noHeader;
import static io.helidon.http.Method.GET;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalToIgnoringCase;

@ServerTest
class ContentEncodingVaryTest {
    private static final String ENTITY = "hello webserver";
    private static final String ETAG = "\"content-encoding\"";
    private static final Header VARY_ACCEPT_ENCODING =
            HeaderValues.createCached(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME);
    private static final Header VARY_ORIGIN =
            HeaderValues.createCached(HeaderNames.VARY, HeaderNames.ORIGIN.defaultCase());

    private final Http2Client client;

    ContentEncodingVaryTest(Http2Client client) {
        this.client = client;
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        server.contentEncoding(ContentEncodingContext.builder()
                                       .addContentEncoding(GzipEncoding.create())
                                       .build());
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder rules) {
        rules.route(Http2Route.route(GET, "/before-send-vary", (_, res) -> {
            res.beforeSend(() -> res.headers().set(VARY_ORIGIN));
            res.send(ENTITY);
        })).route(Http2Route.route(GET, "/not-modified", (req, res) -> {
            res.header(HeaderNames.ETAG, ETAG);
            if (req.headers().contains(HeaderValues.create(HeaderNames.IF_NONE_MATCH, ETAG))) {
                res.status(Status.NOT_MODIFIED_304).send();
            } else {
                res.send(ENTITY);
            }
        }));
    }

    @Test
    void testAutomaticEncodingPreservesBeforeSendVary() {
        try (var response = client.get("/before-send-vary")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(),
                       hasHeaderValue(HeaderNames.CONTENT_ENCODING, equalToIgnoringCase("gzip")));

            var varyValues = response.headers().values(HeaderNames.VARY);
            assertThat("Vary response header " + varyValues,
                       response.headers().containsToken(VARY_ORIGIN), is(true));
            assertThat("Vary response header " + varyValues,
                       response.headers().containsToken(VARY_ACCEPT_ENCODING), is(true));
        }
    }

    @Test
    void testAutomaticEncodingAddsVaryToNotModified() {
        try (var response = client.get("/not-modified")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(),
                       hasHeaderValue(HeaderNames.CONTENT_ENCODING, equalToIgnoringCase("gzip")));

            var varyValues = response.headers().values(HeaderNames.VARY);
            assertThat("Vary response header " + varyValues,
                       response.headers().containsToken(VARY_ACCEPT_ENCODING), is(true));
        }

        try (var response = client.get("/not-modified")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip")
                .header(HeaderNames.IF_NONE_MATCH, ETAG)
                .followRedirects(false)
                .request()) {
            assertThat(response.status(), is(Status.NOT_MODIFIED_304));
            assertThat(response.headers(), noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.entity().as(byte[].class).length, is(0));

            var varyValues = response.headers().values(HeaderNames.VARY);
            assertThat("Vary response header " + varyValues,
                       response.headers().containsToken(VARY_ACCEPT_ENCODING), is(true));
        }
    }
}
