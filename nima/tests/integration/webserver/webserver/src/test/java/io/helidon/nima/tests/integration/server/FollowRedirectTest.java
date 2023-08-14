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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import io.helidon.common.http.Http;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.http.HttpRouting;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.helidon.common.http.Http.Status.INTERNAL_SERVER_ERROR_500;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ServerTest
class FollowRedirectTest {
    private static final StringBuilder BUFFER = new StringBuilder();
    private final Http1Client webClient;

    FollowRedirectTest(Http1Client client) {
        this.webClient = client;
    }

    @SetUpRoute
    static void router(HttpRouting.Builder router) {
        router.route(Http.Method.PUT, "/infiniteRedirect", (req, res) -> {
            res.status(Http.Status.TEMPORARY_REDIRECT_307)
                    .header(Http.HeaderNames.LOCATION, "/infiniteRedirect2")
                    .send();
        }).route(Http.Method.PUT, "/infiniteRedirect2", (req, res) -> {
            res.status(Http.Status.TEMPORARY_REDIRECT_307)
                    .header(Http.HeaderNames.LOCATION, "/infiniteRedirect")
                    .send();
        }).route(Http.Method.PUT, "/redirect", (req, res) -> {
            res.status(Http.Status.TEMPORARY_REDIRECT_307)
                    .header(Http.HeaderNames.LOCATION, "/plain")
                    .send();
        }).route(Http.Method.PUT, "/redirectNoEntity", (req, res) -> {
            res.status(Http.Status.FOUND_302)
                    .header(Http.HeaderNames.LOCATION, "/plain")
                    .send();
        }).route(Http.Method.PUT, "/plain", (req, res) -> {
            try (InputStream in = req.content().inputStream()) {
                byte[] buffer = new byte[128];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    BUFFER.append("\n").append(new String(buffer, 0, read));
                }
                res.send("Test data:" + BUFFER);
            } catch (Exception e) {
                res.status(INTERNAL_SERVER_ERROR_500)
                        .send(e.getMessage());
            }
        }).route(Http.Method.PUT, "/redirectAfterUpload", (req, res) -> {
            try (InputStream in = req.content().inputStream()) {
                byte[] buffer = new byte[128];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    BUFFER.append("\n").append(new String(buffer, 0, read));
                }
                res.status(Http.Status.SEE_OTHER_303)
                        .header(Http.HeaderNames.LOCATION, "/afterUpload")
                        .send();
            } catch (Exception e) {
                res.status(INTERNAL_SERVER_ERROR_500)
                        .send(e.getMessage());
            }
        }).route(Http.Method.GET, "/afterUpload", (req, res) -> {
            res.send("Upload completed!" + BUFFER);
        }).route(Http.Method.GET, "/plain", (req, res) -> {
            res.send("GET plain endpoint reached");
        }).route(Http.Method.PUT, "/close", (req, res) -> {
            byte[] buffer = new byte[10];
            try (InputStream in = req.content().inputStream()) {
                in.read(buffer);
                throw new RuntimeException("BOOM!");
            } catch (IOException e) {
                res.status(INTERNAL_SERVER_ERROR_500)
                        .send(e.getMessage());
            }
        });
    }

    @AfterEach
    void clearBuffer() {
        BUFFER.setLength(0);
    }

    @Test
    void testOutputStreamFollowRedirect() {
        String expected = """
                Test data:
                0123456789
                0123456789
                0123456789""";
        try (Http1ClientResponse response = webClient.put()
                .path("/redirect")
                .outputStream(it -> {
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.close();
                })) {
            assertThat(response.entity().as(String.class), is(expected));
        }
    }

    @Test
    void testOutputStreamEntityNotKept() {
        String expected = "GET plain endpoint reached";
        try (Http1ClientResponse response = webClient.put()
                .path("/redirectNoEntity")
                .outputStream(it -> {
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.close();
                })) {
            assertThat(response.entity().as(String.class), is(expected));
        }
    }

    @Test
    void testEmptyOutputStreamWithRedirectAfter() {
        String expected = "Upload completed!";
        try (Http1ClientResponse response = webClient.put()
                .path("/redirectAfterUpload")
                .outputStream(OutputStream::close)) {
            assertThat(response.entity().as(String.class), is(expected));
        }
    }

    @Test
    void testEntityOutputStreamWithRedirectAfter() {
        String expected = """
                Upload completed!
                0123456789
                0123456789
                0123456789""";
        try (Http1ClientResponse response = webClient.put()
                .path("/redirectAfterUpload")
                .outputStream(it -> {
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.close();
                })) {
            assertThat(response.entity().as(String.class), is(expected));
        }
    }

    @Test
    void testOutputStreamEntityNotKeptIntercepted() {
        String expected = "GET plain endpoint reached";
        try (Http1ClientResponse response = webClient.put()
                .path("/redirectNoEntity")
                .outputStream(it -> {
                    try {
                        it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                        it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                        it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                        it.close();
                    } catch (Exception ignore) {
                    }
                })) {
            assertThat(response.entity().as(String.class), is(expected));
        }
    }

    @Test
    void testMaxNumberOfRedirections() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> webClient.put()
                .path("/infiniteRedirect")
                .outputStream(it -> {
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.close();
                }));
        assertThat(exception.getMessage(), is("Maximum number of request redirections (10) reached."));
    }

}
