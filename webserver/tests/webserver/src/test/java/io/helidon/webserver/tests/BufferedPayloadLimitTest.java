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
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import io.helidon.common.Functions.CheckedFunction;
import io.helidon.common.Size;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.encoding.deflate.DeflateEncoding;
import io.helidon.http.encoding.gzip.GzipEncoding;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class BufferedPayloadLimitTest {
    private static final int MAX_PAYLOAD_SIZE = 128;
    private static final int MAX_BUFFERED_ENTITY_SIZE = 256;
    private static final Header CONTENT_ENCODING_GZIP = HeaderValues.create(HeaderNames.CONTENT_ENCODING, "gzip");
    private static final Header CONTENT_ENCODING_DEFLATE = HeaderValues.create(HeaderNames.CONTENT_ENCODING, "deflate");

    private final Http1Client client;

    BufferedPayloadLimitTest(Http1Client client) {
        this.client = client;
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        server.contentEncoding(ContentEncodingContext.builder()
                        .addContentEncoding(GzipEncoding.create())
                        .addContentEncoding(DeflateEncoding.create())
                        .build())
                .maxPayloadSize(MAX_PAYLOAD_SIZE)
                .addConnectionSelector(Http1ConnectionSelector.builder()
                        .config(Http1Config.builder()
                                .maxBufferedEntitySize(Size.create(MAX_BUFFERED_ENTITY_SIZE))
                                .build())
                        .build());
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.post("/buffer-only", (req, res) -> {
            req.content().buffer();
            res.send();
        });
    }

    @Test
    void configuredDecodedPayloadLimitAppliesToBufferOnly() {
        assertBoundary(CONTENT_ENCODING_GZIP, GZIPOutputStream::new);
        assertBoundary(CONTENT_ENCODING_DEFLATE, DeflaterOutputStream::new);
    }

    private void assertBoundary(Header encoding, CheckedFunction<OutputStream, OutputStream, IOException> factory) {
        byte[] accepted = DecodedEntityLimitTest.encode("A".repeat(MAX_PAYLOAD_SIZE), factory);
        byte[] rejected = DecodedEntityLimitTest.encode("A".repeat(MAX_PAYLOAD_SIZE + 1), factory);
        assertThat(rejected.length < MAX_PAYLOAD_SIZE, is(true));
        try (var response = client.post("/buffer-only").header(encoding).submit(accepted)) {
            assertThat(response.status(), is(Status.OK_200));
        }
        try (var response = client.post("/buffer-only").header(encoding).submit(rejected)) {
            assertThat(response.status(), is(Status.REQUEST_ENTITY_TOO_LARGE_413));
            assertThat(response.headers(), hasHeader(HeaderValues.CONNECTION_CLOSE));
        }
    }
}
