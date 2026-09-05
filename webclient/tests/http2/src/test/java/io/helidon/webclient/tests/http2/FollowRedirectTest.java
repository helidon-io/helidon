/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.tests.http2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClientCookieManager;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientResponse;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.helidon.http.Status.INTERNAL_SERVER_ERROR_500;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ServerTest
class FollowRedirectTest {
    private static final StringBuilder BUFFER = new StringBuilder();
    private static final String PATH_COOKIE = "pathOnly=redirect-secret";
    private static final HeaderName REDIRECT_HEADER = HeaderNames.create("X-Redirect-Test");
    private static final AtomicReference<String> REDIRECT_SOURCE_COOKIE = new AtomicReference<>();
    private static final AtomicReference<String> REDIRECT_TARGET_COOKIE = new AtomicReference<>();
    private final Http2Client webClient;

    FollowRedirectTest(URI uri) {
        this.webClient = Http2Client.builder()
                .baseUri(uri)
                .cookieManager(WebClientCookieManager.builder().automaticStoreEnabled(true).build())
                .build();
    }

    @SetUpRoute
    static void router(HttpRouting.Builder router) {
        router.route(Method.PUT, "/infiniteRedirect", (req, res) -> {
            res.status(Status.TEMPORARY_REDIRECT_307)
                    .header(HeaderNames.LOCATION, "/infiniteRedirect2")
                    .send();
        }).route(Method.PUT, "/infiniteRedirect2", (req, res) -> {
            res.status(Status.TEMPORARY_REDIRECT_307)
                    .header(HeaderNames.LOCATION, "/infiniteRedirect")
                    .send();
        }).route(Method.PUT, "/redirect", (req, res) -> {
            res.status(Status.TEMPORARY_REDIRECT_307)
                    .header(HeaderNames.LOCATION, "/plain")
                    .send();
        }).route(Method.PUT, "/redirectKeepMethodThenGet", (req, res) -> {
            res.status(Status.TEMPORARY_REDIRECT_307)
                    .header(HeaderNames.LOCATION, "/redirectNoEntityAfterKeepMethod")
                    .send();
        }).route(Method.PUT, "/redirectKeepMethodThenRedirectAfterUpload", (req, res) -> {
            res.status(Status.TEMPORARY_REDIRECT_307)
                    .header(HeaderNames.LOCATION, "/redirectAfterUploadDelayed")
                    .send();
        }).route(Method.PUT, "/redirectNoEntity", (req, res) -> {
            res.status(Status.FOUND_302)
                    .header(HeaderNames.LOCATION, "/plain")
                    .send();
        }).route(Method.PUT, "/redirectNoEntityAfterKeepMethod", (req, res) -> {
            res.status(Status.FOUND_302)
                    .header(HeaderNames.LOCATION, "/delayedPlain")
                    .send();
        }).route(Method.PUT, "/redirectAfterUploadDelayed", (req, res) -> {
            try (InputStream in = req.content().inputStream()) {
                byte[] buffer = new byte[128];
                while (in.read(buffer) > 0) {
                    // Do nothing and just drain the entity.
                }
                res.status(Status.SEE_OTHER_303)
                        .header(HeaderNames.LOCATION, "/delayedPlain")
                        .send();
            } catch (Exception e) {
                res.status(INTERNAL_SERVER_ERROR_500)
                        .send(e.getMessage());
            }
        }).route(Method.GET, "/source/prime", (req, res) -> {
            res.header(HeaderNames.SET_COOKIE, PATH_COOKIE + "; Path=/source")
                    .send();
        }).route(Method.PUT, "/source/bounce", (req, res) -> {
            REDIRECT_SOURCE_COOKIE.set(req.headers().contains(HeaderNames.COOKIE)
                                               ? req.headers().get(HeaderNames.COOKIE).values()
                                               : null);
            res.status(Status.create(308, "Custom Permanent Redirect"))
                    .header(HeaderNames.LOCATION, "/target/collect")
                    .send();
        }).route(Method.PUT, "/target/collect", (req, res) -> {
            REDIRECT_TARGET_COOKIE.set(req.headers().contains(HeaderNames.COOKIE)
                                               ? req.headers().get(HeaderNames.COOKIE).values()
                                               : null);
            if (req.headers().contains(REDIRECT_HEADER)) {
                res.status(Status.BAD_REQUEST_400).send("Custom header was preserved");
                return;
            }
            String contentType = req.headers().contentType().orElseThrow().mediaType().text();
            res.send(contentType + ":" + req.content().as(String.class));
        }).route(Method.GET, "/redirectDropEntity", (req, res) -> {
            res.status(Status.FOUND_302)
                    .header(HeaderNames.LOCATION, "/afterDropEntity")
                    .send();
        }).route(Method.GET, "/afterDropEntity", (req, res) -> {
            if (req.content().hasEntity()
                    || req.headers().contains(HeaderNames.CONTENT_TYPE)
                    || req.headers().contains(REDIRECT_HEADER)) {
                res.status(Status.BAD_REQUEST_400).send("Entity metadata was preserved");
                return;
            }
            res.send("GET without entity metadata");
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
                        .header(HeaderNames.LOCATION, "/afterUpload")
                        .send();
            } catch (Exception e) {
                res.status(INTERNAL_SERVER_ERROR_500)
                        .send(e.getMessage());
            }
        }).route(Method.GET, "/afterUpload", (req, res) -> {
            res.send("Upload completed!" + BUFFER);
        }).route(Method.GET, "/plain", (req, res) -> {
            res.send("GET plain endpoint reached");
        }).route(Method.GET, "/delayedPlain", (req, res) -> {
            TimeUnit.MILLISECONDS.sleep(250);
            res.send("GET delayed endpoint reached");
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

    @AfterEach
    void clearBuffer() {
        BUFFER.setLength(0);
        REDIRECT_SOURCE_COOKIE.set(null);
        REDIRECT_TARGET_COOKIE.set(null);
    }

    @Test
    void testOutputStreamFollowRedirect() {
        String expected = """
                Test data:
                0123456789
                0123456789
                0123456789""";
        try (Http2ClientResponse response = webClient.put()
                .path("/redirect")
                .readContinueTimeout(Duration.ofMillis(200))
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
        try (Http2ClientResponse response = webClient.put()
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
    void testReadTimeoutPreservedAcrossMixedRedirects() {
        String expected = "GET delayed endpoint reached";
        try (Http2ClientResponse response = webClient.put()
                .path("/redirectKeepMethodThenGet")
                .readContinueTimeout(Duration.ofMillis(50))
                .readTimeout(Duration.ofSeconds(1))
                .outputStream(it -> {
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.close();
                })) {
            assertThat(response.entity().as(String.class), is(expected));
        }
    }

    @Test
    void testReadTimeoutPreservedAcrossRedirectAfterUpload() {
        String expected = "GET delayed endpoint reached";
        try (Http2ClientResponse response = webClient.put()
                .path("/redirectKeepMethodThenRedirectAfterUpload")
                .readContinueTimeout(Duration.ofMillis(50))
                .readTimeout(Duration.ofSeconds(1))
                .outputStream(it -> {
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.close();
                })) {
            assertThat(response.entity().as(String.class), is(expected));
        }
    }

    @Test
    void methodPreservingRedirectReselectsCookiesForTargetPath() {
        try (Http2ClientResponse response = webClient.get()
                .path("/source/prime")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
        }

        try (Http2ClientResponse response = webClient.put()
                .path("/source/bounce")
                .header(HeaderValues.CONTENT_TYPE_TEXT_PLAIN)
                .header(REDIRECT_HEADER, "drop")
                .submit("entity")) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("text/plain:entity"));
        }

        assertThat(REDIRECT_SOURCE_COOKIE.get(), containsString(PATH_COOKIE));
        assertThat(REDIRECT_TARGET_COOKIE.get(), is(nullValue()));
    }

    @Test
    void sameMethodRedirectDropsEntityHeaders() {
        try (Http2ClientResponse response = webClient.get()
                .path("/redirectDropEntity")
                .header(HeaderValues.CONTENT_TYPE_TEXT_PLAIN)
                .header(REDIRECT_HEADER, "drop")
                .submit("entity")) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("GET without entity metadata"));
        }
    }

    @Test
    void testEmptyOutputStreamWithRedirectAfter() {
        assertEmptyOutputStreamWithRedirectAfter();
    }

    @Test
    void testEntityThenEmptyOutputStreamWithRedirectAfter() {
        assertEntityOutputStreamWithRedirectAfter();
        clearBuffer();
        assertEmptyOutputStreamWithRedirectAfter();
    }

    @Test
    void testEntityOutputStreamWithRedirectAfter() {
        assertEntityOutputStreamWithRedirectAfter();
    }

    private void assertEmptyOutputStreamWithRedirectAfter() {
        String expected = "Upload completed!";
        try (Http2ClientResponse response = webClient.put()
                .path("/redirectAfterUpload")
                .outputStream(OutputStream::close)) {
            assertThat(response.entity().as(String.class), is(expected));
        }
    }

    private void assertEntityOutputStreamWithRedirectAfter() {
        String expected = """
                Upload completed!
                0123456789
                0123456789
                0123456789""";
        try (Http2ClientResponse response = webClient.put()
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
        try (Http2ClientResponse response = webClient.put()
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

    @Test
    void test100ContinueTimeout() {
        // the webclient just starts sending entity (that is the reason for the timeout, for servers that may not send continue)
        ClientResponseTyped<String> http2ClientResponse = webClient.put()
                .path("/wait")
                .readContinueTimeout(Duration.ofMillis(200))
                .outputStream(it -> {
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.close();
                }, String.class);

        assertThat(http2ClientResponse.entity(), is("Request did not timeout"));
    }

}
