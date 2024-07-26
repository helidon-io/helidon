/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.webclient;

import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

public class HeadersTest {

    private static WebServer server;
    private static WebClient client;

    @BeforeAll
    static void beforeAll() throws ExecutionException, InterruptedException, TimeoutException {
        server = WebServer.builder(
                        Routing.builder()
                                .get("/invalidContentType", HeadersTest::invalidContentType)
                                .get("/invalidTextContentType", HeadersTest::invalidTextContentType)
                                .build()
                )
                .build();
        server.start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        client = WebClient.builder()
                .baseUri("http://localhost:" + server.port())
                .validateHeaders(false)
                .keepAlive(true)
                .build();
    }

    @AfterAll
    static void afterAll() throws ExecutionException, InterruptedException, TimeoutException {
        if (server != null) {
            server.shutdown()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        }
    }

    // Verify that invalid content type causes an exception when being parsed
    @Test
    void testInvalidContentType() {
        try {
            client.get()
                    .path("/invalidContentType")
                    .request()
                    .await();
            fail("WebClient shall throw an exception");
        } catch (Exception ex) {
            assertThat(ex, is(instanceOf(CompletionException.class)));
            Throwable cause = ex.getCause();
            assertThat(cause, is(notNullValue()));
            assertThat(cause, is(instanceOf(IllegalArgumentException.class)));
            assertThat(cause.getMessage(), containsString(INVALID_CONTENT_TYPE_VALUE));
        }
    }

    // Verify that "text" content type causes an exception when being parsed in strict mode
    @Test
    void testInvalidTextContentTypeStrict() {
        try {
        client.get()
                .path("/invalidTextContentType")
                .request()
                .await();
        } catch (Exception ex) {
            assertThat(ex, is(instanceOf(CompletionException.class)));
            Throwable cause = ex.getCause();
            assertThat(cause, is(notNullValue()));
            assertThat(cause, is(instanceOf(IllegalArgumentException.class)));
            assertThat(cause.getMessage(), containsString(INVALID_CONTENT_TYPE_TEXT));
        }
    }

    // Verify that "text" content type is transformed to "text/plain" in relaxed mode
    @Test
    void testInvalidTextContentTypeRelaxed() {
        WebClient client = WebClient.builder()
                .baseUri("http://localhost:" + server.port())
                .validateHeaders(false)
                .keepAlive(true)
                .mediaTypeParserRelaxed(true)
                .build();
        WebClientResponse response = client.get()
                .path("/invalidTextContentType")
                .request()
                .await();
        Optional<MediaType> maybeContentType = response.headers().contentType();
        assertThat(maybeContentType.isPresent(), is(true));
        assertThat(maybeContentType.get().toString(), is(RELAXED_CONTENT_TYPE_TEXT));
    }

    private static final String INVALID_CONTENT_TYPE_VALUE = "invalid header value";

    // HTTP service with invalid Content-Type
    private static void invalidContentType(ServerRequest request, ServerResponse response) {
        response.addHeader(Http.Header.CONTENT_TYPE, INVALID_CONTENT_TYPE_VALUE)
                .send();
    }

    private static final String INVALID_CONTENT_TYPE_TEXT = "text";
    private static final String RELAXED_CONTENT_TYPE_TEXT = "text/plain";

    // HTTP service with Content-Type: text instead of text/plain
    private static void invalidTextContentType(ServerRequest request, ServerResponse response) {
        response.addHeader(Http.Header.CONTENT_TYPE, INVALID_CONTENT_TYPE_TEXT)
                .send();
    }

}
