/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests that verify 413 conditions with very large payloads.
 */
@ServerTest
class MaxPayloadSizeTest {
    private static final long MAX_PAYLOAD_SIZE = 128L;
    private static final String PAYLOAD = "A".repeat(1024);
    private static final byte[] PAYLOAD_BYTES = PAYLOAD.getBytes(StandardCharsets.UTF_8);

    private final Http1Client client;

    MaxPayloadSizeTest(Http1Client client) {
        this.client = client;
    }

    @SetUpServer
    static void setupServer(WebServerConfig.Builder builder) {
        builder.maxPayloadSize(MAX_PAYLOAD_SIZE);
    }

    @SetUpRoute
    static void setupRoute(HttpRules rules) {
        rules.post("/maxpayload", (req, res) -> {
            try (InputStream in = req.content().inputStream()) {
                byte[] content = in.readAllBytes();
                res.send("read + " + content.length + " bytes");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /**
     * If content length is greater than max, a 413 must be returned.
     */
    @Test
    void testContentLengthExceededWithPayload() {
        try (Http1ClientResponse response = client.method(Method.POST)
                .path("/maxpayload")
                .header(HeaderValues.CONTENT_TYPE_OCTET_STREAM)
                .submit(PAYLOAD)) {
            assertThat(response.status(), is(Status.REQUEST_ENTITY_TOO_LARGE_413));
            assertThat(response.headers(), hasHeader(HeaderValues.CONNECTION_CLOSE));
        }
    }

    /**
     * If actual payload length is greater than max when using chunked encoding, a 413
     * must be returned. Given that this publisher can write up to 3 chunks (using chunked
     * encoding), we also check for a connection reset exception condition.
     */
    @Test
    void testActualLengthExceededWithPayload() {
        try (Http1ClientResponse response = client.method(Method.POST)
                .path("/maxpayload")
                .header(HeaderValues.CONTENT_TYPE_OCTET_STREAM)
                .header(HeaderValues.TRANSFER_ENCODING_CHUNKED)
                .outputStream(it -> {
                    try (it) {
                        it.write(PAYLOAD_BYTES);
                        it.write(PAYLOAD_BYTES);
                        it.write(PAYLOAD_BYTES);
                    } catch (IOException e) {
                        // ignored -- possible connection reset
                    }
                })) {
            assertThat(response.status(), is(Status.REQUEST_ENTITY_TOO_LARGE_413));
            assertThat(response.headers(), hasHeader(HeaderValues.CONNECTION_CLOSE));
        }
    }

    /**
     * Tests mixed requests, some that exceed limits, others that do not.
     */
    @Test
    void testMixedGoodAndBadPayloads() {
        try (Http1ClientResponse response = client.method(Method.POST)
                .path("/maxpayload")
                .header(HeaderValues.CONTENT_TYPE_OCTET_STREAM)
                .submit(PAYLOAD.substring(0, 100))) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), hasHeader(HeaderValues.CONNECTION_KEEP_ALIVE));
        }

        try (Http1ClientResponse response = client.method(Method.POST)
                .path("/maxpayload")
                .header(HeaderValues.CONTENT_TYPE_OCTET_STREAM)
                .submit(PAYLOAD)) {
            assertThat(response.status(), is(Status.REQUEST_ENTITY_TOO_LARGE_413));
            assertThat(response.headers(), hasHeader(HeaderValues.CONNECTION_CLOSE));
        }

        try (Http1ClientResponse response = client.method(Method.POST)
                .path("/maxpayload")
                .header(HeaderValues.CONTENT_TYPE_OCTET_STREAM)
                .submit(PAYLOAD.substring(0, (int) MAX_PAYLOAD_SIZE))) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), hasHeader(HeaderValues.CONNECTION_KEEP_ALIVE));
        }
    }
}
