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

package io.helidon.webserver.tests;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.InternalServerException;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class OutputStreamAndContentLengthTest {
    private static byte[] bytes;
    private static byte[] smallBytes;
    private final WebClient client;

    OutputStreamAndContentLengthTest(WebClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.post("/chunked", OutputStreamAndContentLengthTest::chunked)
                .post("/content-length/good", OutputStreamAndContentLengthTest::goodContentLength)
                .post("/content-length/out-of-order", OutputStreamAndContentLengthTest::outOfOrderContentLength)
                .post("/content-length/out-of-order-small", OutputStreamAndContentLengthTest::outOfOrderContentLengthSmall);
    }

    @BeforeEach
    void beforeEach() {
        OutputStreamAndContentLengthTest.bytes = new byte[1_000_000]; // 1 MBi
        new Random().nextBytes(bytes);
        OutputStreamAndContentLengthTest.smallBytes = new byte[32];
        new Random().nextBytes(smallBytes);
    }

    @AfterEach
    void afterEach() {
        OutputStreamAndContentLengthTest.bytes = null;
        OutputStreamAndContentLengthTest.smallBytes = null;
    }

    @Test
    void testOutputStreamChunked() {
        ClientResponseTyped<byte[]> response = client.post("/chunked")
                .outputStream(it -> {
                    try (it) {
                        new ByteArrayInputStream(bytes).transferTo(it);
                    }
                }, byte[].class);

        assertThat(response.headers(), hasHeader(HeaderValues.TRANSFER_ENCODING_CHUNKED));
        assertThat(response.headers(), not(hasHeader(HeaderNames.CONTENT_LENGTH)));
        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is(bytes));
    }

    @Test
    void testOutputStreamWithContentLength() {
        ClientResponseTyped<byte[]> response = client.post("/content-length/good")
                .outputStream(it -> {
                    try (it) {
                        new ByteArrayInputStream(bytes).transferTo(it);
                    }
                }, byte[].class);

        assertThat(response.headers(), not(hasHeader(HeaderValues.TRANSFER_ENCODING_CHUNKED)));
        assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_LENGTH));
        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is(bytes));
    }

    @Test
    void testOutputStreamOutOfOrder() {
        ClientResponseTyped<byte[]> response = client.post("/content-length/out-of-order")
                .outputStream(it -> {
                    try (it) {
                        new ByteArrayInputStream(bytes).transferTo(it);
                    }
                }, byte[].class);

        assertThat(response.headers(), hasHeader(HeaderValues.TRANSFER_ENCODING_CHUNKED));
        assertThat(response.headers(), not(hasHeader(HeaderNames.CONTENT_LENGTH)));
        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is(bytes));
    }

    @Test
    void testOutputStreamOutOfOrderSmallEntity() {
        // this entity must be small enough to trigger content length optimization
        ClientResponseTyped<byte[]> response = client.post("/content-length/out-of-order-small")
                .outputStream(it -> {
                    try (it) {
                        new ByteArrayInputStream(smallBytes).transferTo(it);
                    }
                }, byte[].class);

        assertThat(response.headers(), hasHeader(HeaderValues.TRANSFER_ENCODING_CHUNKED));
        assertThat(response.headers(), not(hasHeader(HeaderNames.CONTENT_LENGTH)));
        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is(smallBytes));
    }

    private static void chunked(ServerRequest req, ServerResponse res) throws IOException {
        try (OutputStream out = res.outputStream(); InputStream in = req.content().inputStream()) {
            in.transferTo(out);
        }

    }

    private static void goodContentLength(ServerRequest req, ServerResponse res) throws IOException {
        // should not be chunked, as we have a content length
        res.contentLength(bytes.length);
        try (OutputStream out = res.outputStream(); InputStream in = req.content().inputStream()) {
            in.transferTo(out);
        }
    }

    private static void outOfOrderContentLength(ServerRequest req, ServerResponse res) throws IOException {
        // should transfer all data and not fail
        try (OutputStream out = res.outputStream(); InputStream in = req.content().inputStream()) {
            in.transferTo(out);
        }
        try {
            res.contentLength(bytes.length);
            throw new InternalServerException("Content length cannot be set after stream was requested", new RuntimeException());
        } catch (IllegalStateException ignored) {
            // this is expected
        }
    }

    private static void outOfOrderContentLengthSmall(ServerRequest req, ServerResponse res) throws IOException {
        // should transfer all data and not fail
        OutputStream out = res.outputStream(); // intentionally not closing output stream
        try (InputStream in = req.content().inputStream()) {
            in.transferTo(out);
        }
        try {
            res.contentLength(smallBytes.length);
            throw new InternalServerException("Content length cannot be set after stream was requested", new RuntimeException());
        } catch (IllegalStateException ignored) {
            // this is expected
        }
    }
}
