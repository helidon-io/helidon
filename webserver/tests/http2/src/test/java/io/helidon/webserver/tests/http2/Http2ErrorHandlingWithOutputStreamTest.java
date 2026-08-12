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

package io.helidon.webserver.tests.http2;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.ErrorHandler;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import static io.helidon.http.Method.GET;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ServerTest
class Http2ErrorHandlingWithOutputStreamTest {

    private static final HeaderName MAIN_HEADER_NAME = HeaderNames.create("main-handler");
    private static final HeaderName ERROR_HEADER_NAME = HeaderNames.create("error-handler");
    private static final HeaderName REENTRY_RESULT_HEADER_NAME = HeaderNames.create("reentry-result");
    private static final HeaderName CALLBACK_COUNT_HEADER_NAME = HeaderNames.create("callback-count");
    private static HttpClient httpClient;
    private final int plainPort;
    private final int tlsPort;

    Http2ErrorHandlingWithOutputStreamTest(WebServer server) {
        this.plainPort = server.port();
        this.tlsPort = server.port("https");
    }

    public static <T> Matcher<? super Optional<T>> emptyOptional() {
        return new EmptyOptionalMatcher<>();
    }

    @SetUpServer
    static void setUpServer(WebServerConfig.Builder serverBuilder) {
        Keys privateKeyConfig = Keys.builder()
                .keystore(store -> store
                        .passphrase("password")
                        .keystore(Resource.create("server.p12")))
                .build();

        Tls tls = Tls.builder()
                .privateKey(privateKeyConfig)
                .privateKeyCertChain(privateKeyConfig)
                .build();

        serverBuilder.putSocket("https",
                                socketBuilder -> socketBuilder.tls(tls));
        httpClient = http2Client();
    }

    @SetUpRoute
    static void router(HttpRouting.Builder router) {
        // explicitly on HTTP/2 only, to make sure we do upgrade
        router.error(CustomException.class, new CustomRoutingHandler())
                .route(Http2Route.route(GET, "get-outputStream", (req, res) -> {
                    res.status(Status.OK_200);
                    res.header(MAIN_HEADER_NAME, "x");
                    res.header(HeaderNames.CONTENT_LENGTH, "1");
                    res.header(HeaderNames.TRAILER, "x-stale-trailer");
                    res.header(HeaderNames.CONTENT_RANGE, "bytes 0-0/1");
                    res.header(HeaderNames.CONTENT_TYPE, "application/stale");
                    res.header(HeaderNames.CONTENT_ENCODING, "stale");
                    res.header(HeaderNames.CONTENT_LANGUAGE, "en");
                    res.header(HeaderNames.CONTENT_LOCATION, "/stale");
                    res.header(HeaderNames.CONTENT_DISPOSITION, "attachment");
                    res.header(HeaderNames.ETAG, "\"stale\"");
                    res.header(HeaderNames.LAST_MODIFIED, "stale");
                    res.header(HeaderNames.ACCEPT_RANGES, "bytes");
                    res.header(HeaderNames.CACHE_CONTROL, "no-store");
                    res.header(HeaderNames.VARY, "Origin");
                    res.streamFilter(_ -> OutputStream.nullOutputStream());
                    res.outputStream();
                    throw new CustomException();
                }))
                .route(Http2Route.route(GET, "get-outputStream-writeOnceThenError", (req, res) -> {
                    res.status(Status.OK_200);
                    res.header(MAIN_HEADER_NAME, "x");
                    OutputStream os = res.outputStream();
                    os.write("writeOnceOnly".getBytes(StandardCharsets.UTF_8));
                    throw new CustomException();
                }))
                .route(Http2Route.route(GET, "get-outputStream-writeTwiceThenError", (req, res) -> {
                    res.status(Status.OK_200);
                    res.header(MAIN_HEADER_NAME, "x");
                    OutputStream os = res.outputStream();
                    os.write("writeOnce".getBytes(StandardCharsets.UTF_8));
                    os.write("|writeTwice".getBytes(StandardCharsets.UTF_8));
                    throw new CustomException();
                }))
                .route(Http2Route.route(GET, "get-outputStream-writeFlushThenError", (req, res) -> {
                    res.status(Status.OK_200);
                    res.header(MAIN_HEADER_NAME, "x");
                    OutputStream os = res.outputStream();
                    os.write("writeOnce".getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    throw new CustomException();
                }))
                .route(Http2Route.route(GET, "get-outputStream-changeStatus", (req, res) -> {
                    try (OutputStream os = res.outputStream()) {
                        String statusResult;
                        try {
                            res.status(Status.INTERNAL_SERVER_ERROR_500);
                            statusResult = "status accepted";
                        } catch (IllegalStateException e) {
                            statusResult = e.getMessage();
                        }
                        String statusCodeResult;
                        try {
                            res.status(500);
                            statusCodeResult = "status code accepted";
                        } catch (IllegalStateException e) {
                            statusCodeResult = e.getMessage();
                        }
                        os.write((statusResult + '|' + statusCodeResult).getBytes(StandardCharsets.UTF_8));
                    }
                }))
                .route(Http2Route.route(GET, "get-outputStream-beforeSend-changeStatus", (req, res) -> {
                    res.beforeSend(() -> res.status(Status.ACCEPTED_202));
                    try (OutputStream os = res.outputStream()) {
                        os.write("before send status accepted".getBytes(StandardCharsets.UTF_8));
                    }
                }))
                .route(Http2Route.route(GET, "beforeSend-reentrant-send", (req, res) -> {
                    AtomicInteger callbackCount = new AtomicInteger();
                    res.beforeSend(() -> {
                        int count = callbackCount.incrementAndGet();
                        res.header(CALLBACK_COUNT_HEADER_NAME, String.valueOf(count));
                        if (count == 1) {
                            String sendResult = reentryResult(() -> res.send("nested"));
                            String outputStreamResult = reentryResult(res::outputStream);
                            res.header(REENTRY_RESULT_HEADER_NAME, sendResult + '|' + outputStreamResult);
                        }
                    });
                    res.send("outer send");
                }))
                .route(Http2Route.route(GET, "beforeSend-reentrant-outputStream", (req, res) -> {
                    AtomicInteger callbackCount = new AtomicInteger();
                    res.beforeSend(() -> {
                        int count = callbackCount.incrementAndGet();
                        res.header(CALLBACK_COUNT_HEADER_NAME, String.valueOf(count));
                        if (count == 1) {
                            String outputStreamResult = reentryResult(res::outputStream);
                            String sendResult = reentryResult(() -> res.send("nested"));
                            res.header(REENTRY_RESULT_HEADER_NAME, outputStreamResult + '|' + sendResult);
                        }
                    });
                    try (OutputStream os = res.outputStream()) {
                        os.write("outer stream".getBytes(StandardCharsets.UTF_8));
                    }
                }))
                .get("get-outputStream-tryWithResources", (req, res) -> {
                    res.header(MAIN_HEADER_NAME, "x");
                    try (OutputStream os = res.outputStream()) {
                        os.write("This should not be sent immediately".getBytes(StandardCharsets.UTF_8));
                        throw new CustomException();
                    }
                })
                .route(Http2Route.route(GET, "", (
                        (req, res) ->
                                res.send("ok"))));
    }

    @Test
    void testOk() {
        var response = request("/");

        assertThat(response.statusCode(), is(200));
        assertThat(response.body(), is("ok"));
    }

    @Test
    void testGetOutputStreamThenError_expect_CustomErrorHandlerMessage() {
        var response = request("/get-outputStream");

        assertThat(response.statusCode(), is(418));
        assertThat(response.body(), is("TeaPotIAm"));
        assertThat(response.headers().firstValue(ERROR_HEADER_NAME.lowerCase()), is(Optional.of("err")));
        assertThat(response.headers().firstValue(MAIN_HEADER_NAME.lowerCase()), is(emptyOptional()));
        assertThat(response.headers().firstValueAsLong(HeaderNames.CONTENT_LENGTH.lowerCase()).orElse(-1),
                   is((long) "TeaPotIAm".getBytes(StandardCharsets.UTF_8).length));
        assertThat(response.headers().firstValue(HeaderNames.TRAILER.lowerCase()), is(emptyOptional()));
        assertThat(response.headers().firstValue(HeaderNames.CONTENT_RANGE.lowerCase()), is(emptyOptional()));
        assertThat(response.headers().firstValue(HeaderNames.CONTENT_TYPE.lowerCase()),
                   is(Optional.of("text/plain; charset=UTF-8")));
        assertThat(response.headers().firstValue(HeaderNames.CONTENT_ENCODING.lowerCase()), is(emptyOptional()));
        assertThat(response.headers().firstValue(HeaderNames.CONTENT_LANGUAGE.lowerCase()), is(emptyOptional()));
        assertThat(response.headers().firstValue(HeaderNames.CONTENT_LOCATION.lowerCase()), is(emptyOptional()));
        assertThat(response.headers().firstValue(HeaderNames.CONTENT_DISPOSITION.lowerCase()), is(emptyOptional()));
        assertThat(response.headers().firstValue(HeaderNames.ETAG.lowerCase()), is(emptyOptional()));
        assertThat(response.headers().firstValue(HeaderNames.LAST_MODIFIED.lowerCase()), is(emptyOptional()));
        assertThat(response.headers().firstValue(HeaderNames.ACCEPT_RANGES.lowerCase()), is(emptyOptional()));
        assertThat(response.headers().firstValue(HeaderNames.CACHE_CONTROL.lowerCase()), is(Optional.of("no-store")));
        assertThat(response.headers().firstValue(HeaderNames.VARY.lowerCase()), is(Optional.of("Origin")));
    }

    @Test
    void testGetOutputStreamWriteOnceThenError_expect_CustomErrorHandlerMessage() {
        var response = request("/get-outputStream-writeOnceThenError");

        assertThat(response.statusCode(), is(418));
        assertThat(response.body(), is("TeaPotIAm"));
        assertThat(response.headers().firstValue(ERROR_HEADER_NAME.lowerCase()), is(Optional.of("err")));
        assertThat(response.headers().firstValue(MAIN_HEADER_NAME.lowerCase()), is(emptyOptional()));
    }

    @Test
    void testGetOutputStreamWriteTwiceThenError_expect_invalidResponse() {
        RuntimeException r = assertThrows(RuntimeException.class, () -> request("/get-outputStream-writeTwiceThenError"));
        assertThat(r.getCause(), instanceOf(IOException.class));
        // stream should have been reset during processing
        assertThat(r.getMessage(), containsString("RST_STREAM"));
    }

    @Test
    void testGetOutputStreamWriteFlushThenError_expect_invalidResponse() {
        RuntimeException r = assertThrows(RuntimeException.class, () -> request("/get-outputStream-writeFlushThenError"));
        assertThat(r.getCause(), instanceOf(IOException.class));
        // stream should have been reset during processing
        assertThat(r.getMessage(), containsString("RST_STREAM"));
    }

    @Test
    void testGetOutputStreamThenChangeStatusExpectFailure() {
        var response = request("/get-outputStream-changeStatus");

        assertThat(response.statusCode(), is(200));
        assertThat(response.body(), is("Cannot set response status after requesting output stream."
                                               + "|Cannot set response status after requesting output stream."));
    }

    @Test
    void testGetOutputStreamBeforeSendChangeStatus() {
        var response = request("/get-outputStream-beforeSend-changeStatus");

        assertThat(response.statusCode(), is(202));
        assertThat(response.body(), is("before send status accepted"));
    }

    @Test
    void testBeforeSendRejectsReentrantSendAndOutputStream() {
        var response = request("/beforeSend-reentrant-send");

        assertThat(response.statusCode(), is(200));
        assertThat(response.body(), is("outer send"));
        assertThat(response.headers().firstValue(CALLBACK_COUNT_HEADER_NAME.lowerCase()), is(Optional.of("1")));
        assertThat(response.headers().firstValue(REENTRY_RESULT_HEADER_NAME.lowerCase()),
                   is(Optional.of("rejected|rejected")));
    }

    @Test
    void testBeforeSendRejectsReentrantOutputStreamAndSend() {
        var response = request("/beforeSend-reentrant-outputStream");

        assertThat(response.statusCode(), is(200));
        assertThat(response.body(), is("outer stream"));
        assertThat(response.headers().firstValue(CALLBACK_COUNT_HEADER_NAME.lowerCase()), is(Optional.of("1")));
        assertThat(response.headers().firstValue(REENTRY_RESULT_HEADER_NAME.lowerCase()),
                   is(Optional.of("rejected|rejected")));
    }

    @Test
    void testGetOutputStreamTryWithResourcesThenError_expect_CustomErrorHandlerMessage() {
        var response = request("/get-outputStream-tryWithResources");

        assertThat(response.statusCode(), is(418));
        assertThat(response.body(), is("TeaPotIAm"));
        assertThat(response.headers().firstValue(ERROR_HEADER_NAME.lowerCase()), is(Optional.of("err")));
        assertThat(response.headers().firstValue(MAIN_HEADER_NAME.lowerCase()), is(emptyOptional()));
    }

    private static String reentryResult(Runnable action) {
        try {
            action.run();
            return "accepted";
        } catch (IllegalStateException e) {
            return "rejected";
        }
    }

    private static HttpClient http2Client() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    private HttpResponse<String> request(String uriSuffix) {
        try {
            return httpClient.send(httpRequest(uriSuffix), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private HttpRequest httpRequest(String uriSuffix) {
        return HttpRequest.newBuilder()
                .timeout(Duration.ofSeconds(30))
                .uri(URI.create("http://localhost:" + plainPort + uriSuffix))
                .GET()
                .build();
    }

    private static class CustomRoutingHandler implements ErrorHandler<CustomException> {
        @Override
        public void handle(ServerRequest req, ServerResponse res, CustomException throwable) {
            res.status(Status.I_AM_A_TEAPOT_418);
            res.header(ERROR_HEADER_NAME, "err");
            // this is now the responsibility of an error handler, as otherwise we may remove CORS headers etc.
            res.headers().remove(MAIN_HEADER_NAME);
            res.send("TeaPotIAm");
        }
    }

    private static class CustomException extends RuntimeException {

    }

    static class EmptyOptionalMatcher<T> extends BaseMatcher<Optional<T>> {

        private Optional<T> optionalActual;

        public EmptyOptionalMatcher() {
        }

        @Override
        public boolean matches(Object item) {
            optionalActual = (Optional<T>) item;

            if (optionalActual == null) {
                return false;
            }

            boolean empty = !optionalActual.isPresent();
            return empty;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("optional is empty");
        }

        @Override
        public void describeMismatch(Object item, Description description) {
            if (optionalActual == null) {
                description.appendText(" optional was NULL?");
            } else {
                description.appendText(" was: " + optionalActual.get());
            }
        }

    }

}
