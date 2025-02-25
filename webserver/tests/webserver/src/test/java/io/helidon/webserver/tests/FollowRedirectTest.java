/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.helidon.http.Status.INTERNAL_SERVER_ERROR_500;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ServerTest
class FollowRedirectTest {
    private static final HeaderName REDIRECT_TO_OTHER_NAME = HeaderNames.create("redirect-to-other");
    private static final Header REDIRECT_TO_OTHER = HeaderValues.create(REDIRECT_TO_OTHER_NAME, "redirectToOtherPort");
    private static final StringBuilder BUFFER = new StringBuilder();
    private final Http1Client webClient;
    private static int otherPort;

    FollowRedirectTest(WebServer server, Http1Client client) {
        this.webClient = client;
        otherPort = server.port("other");
    }

    @SetUpRoute("other")
    static void otherRouter(HttpRouting.Builder router) {
        router.route(Method.PUT, "/infiniteRedirect", (req, res) -> {
            res.status(Status.TEMPORARY_REDIRECT_307)
                    .header(redirectLocation(req, "/infiniteRedirect2"))
                    .send();
        }).route(Method.PUT, "/infiniteRedirect2", (req, res) -> {
            res.status(Status.TEMPORARY_REDIRECT_307)
                    .header(redirectLocation(req, "/infiniteRedirect"))
                    .send();
        }).route(Method.PUT, "/redirect", (req, res) -> {
            res.status(Status.TEMPORARY_REDIRECT_307)
                    .header(redirectLocation(req, "/plain"))
                    .send();
        }).route(Method.PUT, "/redirectNoEntity", (req, res) -> {
            res.status(Status.FOUND_302)
                    .header(redirectLocation(req, "/plain"))
                    .send();
        }).route(Method.PUT, "/plain", (req, res) -> {
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
        }).route(Method.PUT, "/redirectAfterUpload", (req, res) -> {
            try (InputStream in = req.content().inputStream()) {
                byte[] buffer = new byte[128];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    BUFFER.append("\n").append(new String(buffer, 0, read));
                }
                res.status(Status.SEE_OTHER_303)
                        .header(redirectLocation(req, "/afterUpload"))
                        .send();
            } catch (Exception e) {
                res.status(INTERNAL_SERVER_ERROR_500)
                        .send(e.getMessage());
            }
        }).route(Method.GET, "/afterUpload", (req, res) -> {
            res.send("Upload completed!" + BUFFER);
        }).route(Method.GET, "/plain", (req, res) -> {
            res.send("GET plain endpoint reached");
        }).route(Method.PUT, "/close", (req, res) -> {
            byte[] buffer = new byte[10];
            try (InputStream in = req.content().inputStream()) {
                in.read(buffer);
                throw new RuntimeException("BOOM!");
            } catch (IOException e) {
                res.status(INTERNAL_SERVER_ERROR_500)
                        .send(e.getMessage());
            }
        }).route(Method.PUT, "/wait", (req, res) -> {
            TimeUnit.MILLISECONDS.sleep(500);
            try (InputStream in = req.content().inputStream()) {
                byte[] buffer = new byte[128];
                while (in.read(buffer) > 0) {
                    //Do nothing and just drain the entity
                }
                res.send("Request did not timeout");
            } catch (Exception e) {
                res.status(INTERNAL_SERVER_ERROR_500)
                        .send(e.getMessage());
            }
        });
    }
    @SetUpRoute
    static void router(HttpRouting.Builder router) {
        // routes are common for both sockets
        otherRouter(router);
    }

    private static Header redirectLocation(ServerRequest req, String context) {
        if (req.headers().contains(REDIRECT_TO_OTHER)) {
            return HeaderValues.create(HeaderNames.LOCATION, "http://localhost:" + otherPort + context);
        } else {
            return HeaderValues.create(HeaderNames.LOCATION, context);
        }
    }

    @AfterEach
    void clearBuffer() {
        BUFFER.setLength(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"redirectToOtherPort", "redirectLocally"})
    void testOutputStreamFollowRedirect(String redirectToOtherPort) {
        String expected = """
                Test data:
                0123456789
                0123456789
                0123456789""";
        try (Http1ClientResponse response = webClient.put()
                .path("/redirect")
                .header(REDIRECT_TO_OTHER_NAME, redirectToOtherPort)
                .outputStream(it -> {
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.close();
                })) {
            assertThat(response.entity().as(String.class), is(expected));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"redirectToOtherPort", "redirectLocally"})
    void testOutputStreamEntityNotKept(String redirectToOtherPort) {
        String expected = "GET plain endpoint reached";
        try (Http1ClientResponse response = webClient.put()
                .path("/redirectNoEntity")
                .header(REDIRECT_TO_OTHER_NAME, redirectToOtherPort)
                .outputStream(it -> {
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.close();
                })) {
            assertThat(response.entity().as(String.class), is(expected));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"redirectToOtherPort", "redirectLocally"})
    void testEmptyOutputStreamWithRedirectAfter(String redirectToOtherPort) {
        String expected = "Upload completed!";
        try (Http1ClientResponse response = webClient.put()
                .path("/redirectAfterUpload")
                .header(REDIRECT_TO_OTHER_NAME, redirectToOtherPort)
                .outputStream(OutputStream::close)) {
            assertThat(response.entity().as(String.class), is(expected));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"redirectToOtherPort", "redirectLocally"})
    void testEntityOutputStreamWithRedirectAfter(String redirectToOtherPort) {
        String expected = """
                Upload completed!
                0123456789
                0123456789
                0123456789""";
        try (Http1ClientResponse response = webClient.put()
                .path("/redirectAfterUpload")
                .header(REDIRECT_TO_OTHER_NAME, redirectToOtherPort)
                .outputStream(it -> {
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.close();
                })) {
            assertThat(response.entity().as(String.class), is(expected));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"redirectToOtherPort", "redirectLocally"})
    void testOutputStreamEntityNotKeptIntercepted(String redirectToOtherPort) {
        String expected = "GET plain endpoint reached";
        try (Http1ClientResponse response = webClient.put()
                .path("/redirectNoEntity")
                .header(REDIRECT_TO_OTHER_NAME, redirectToOtherPort)
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

    @ParameterizedTest
    @ValueSource(strings = {"redirectToOtherPort", "redirectLocally"})
    void testMaxNumberOfRedirections(String redirectToOtherPort) {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> webClient.put()
                .path("/infiniteRedirect")
                .header(REDIRECT_TO_OTHER_NAME, redirectToOtherPort)
                .outputStream(it -> {
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.close();
                }));
        assertThat(exception.getMessage(), is("Maximum number of request redirections (10) reached."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"redirectToOtherPort", "redirectLocally"})
    void test100ContinueTimeout(String redirectToOtherPort) {
        // the webclient just starts sending entity (that is the reason for the timeout, for servers that may not send continue)
        ClientResponseTyped<String> http1ClientResponse = webClient.put()
                .path("/wait")
                .keepAlive(false)
                .readContinueTimeout(Duration.ofMillis(200))
                .header(REDIRECT_TO_OTHER_NAME, redirectToOtherPort)
                .outputStream(it -> {
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.close();
                }, String.class);

        assertThat(http1ClientResponse.entity(), is("Request did not timeout"));
    }

}
