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

package io.helidon.webclient.http1;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.GenericType;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.EntityReader;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.MediaContextConfig;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.HttpClientRequest;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.WebClientCookieManager;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webclient.spi.WebClientTransportObserverProvider;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.noHeader;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/*
This class uses package local API to validate connection cache, and at the same time benefits from @ServerTest
that is why this tests is in this module, but in the wrong package
 */
@ServerTest
class Http1ClientTest {
    private static final Header REQ_CHUNKED_HEADER = HeaderValues.createCached(
            HeaderNames.create("X-Req-Chunked"), "true");
    private static final Header REQ_EXPECT_100_HEADER_NAME = HeaderValues.createCached(
            HeaderNames.create("X-Req-Expect100"), "true");
    private static final HeaderName REQ_CONTENT_LENGTH_HEADER_NAME = HeaderNames.create("X-Req-ContentLength");
    private static final HeaderName REQUEST_METADATA_HEADER = HeaderNames.create("X-Request-Metadata");
    private static final HeaderName REDIRECT_METHOD = HeaderNames.create("X-Redirect-Method");
    private static final HeaderName REDIRECT_ENTITY = HeaderNames.create("X-Redirect-Entity");
    private static final String EXPECTED_GET_AFTER_REDIRECT_STRING = "GET after redirect endpoint reached";
    private static final String QUERY_ACCEPT = "application/json";
    private static final String QUERY_CONTENT_TYPE = "application/sql";
    private static final String QUERY_ENTITY = "select * from example";
    private static final String QUERY_LANGUAGE = "en";
    private static final long NO_CONTENT_LENGTH = -1L;
    private static volatile CountDownLatch lifecycleUploadReceived = new CountDownLatch(0);
    private static volatile CountDownLatch lifecycleResponseRelease = new CountDownLatch(0);

    private final String baseURI;
    private final WebClient injectedHttp1client;
    private final int plainPort;

    Http1ClientTest(WebServer webServer, WebClient client) {
        baseURI = "http://localhost:" + webServer.port();
        plainPort = webServer.port();
        injectedHttp1client = client;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.put("/test", Http1ClientTest::responseHandler);
        rules.put("/redirectKeepMethod", Http1ClientTest::redirectKeepMethod);
        rules.put("/redirectChainStart", Http1ClientTest::redirectChainStart);
        rules.put("/redirectChainSecond", Http1ClientTest::redirectChainSecond);
        rules.put("/redirectChainFinal", Http1ClientTest::redirectChainFinal);
        rules.put("/redirect", Http1ClientTest::redirect);
        for (Status status : List.of(Status.MOVED_PERMANENTLY_301, Status.FOUND_302)) {
            rules.any("/methodRedirect" + status.code(), (_, res) -> res.status(status)
                    .header(HeaderNames.LOCATION, "/methodRedirectTarget")
                    .send());
        }
        rules.any("/methodRedirectTarget", (req, res) -> res
                .header(REDIRECT_METHOD, req.prologue().method().text())
                .header(REDIRECT_ENTITY, req.content().hasEntity() ? req.content().as(String.class) : "")
                .send());
        rules.route(Method.QUERY,
                    "/queryRedirect301",
                    (_, res) -> queryRedirect(res, Status.MOVED_PERMANENTLY_301, "/queryRedirectTarget"));
        rules.route(Method.QUERY,
                    "/queryRedirect302",
                    (_, res) -> queryRedirect(res, Status.FOUND_302, "/queryRedirectTarget"));
        rules.route(Method.QUERY,
                    "/queryRedirect303",
                    (_, res) -> queryRedirect(res, Status.SEE_OTHER_303, "/queryRedirectGetTarget"));
        rules.route(Method.QUERY,
                    "/queryRedirectHeaders301",
                    (_, res) -> queryRedirect(res, Status.MOVED_PERMANENTLY_301, "/queryRedirectHeadersTarget"));
        rules.route(Method.QUERY,
                    "/queryRedirectHeaders302",
                    (_, res) -> queryRedirect(res, Status.FOUND_302, "/queryRedirectHeadersTarget"));
        rules.route(Method.QUERY, "/queryRedirectTarget", Http1ClientTest::queryRedirectTarget);
        rules.route(Method.QUERY, "/queryRedirectHeadersTarget", Http1ClientTest::queryRedirectHeadersTarget);
        rules.get("/queryRedirectGetTarget", Http1ClientTest::queryRedirectGetTarget);
        rules.get("/redirectDropEntity", Http1ClientTest::redirectDropEntity);
        rules.get("/afterDropEntity", Http1ClientTest::afterDropEntity);
        rules.put("/lifecycle/start", (req, res) -> res.status(Status.TEMPORARY_REDIRECT_307)
                .header(HeaderNames.LOCATION, "/lifecycle/hop")
                .send());
        rules.put("/lifecycle/hop", (req, res) -> {
            req.content().as(String.class);
            res.status(Status.PERMANENT_REDIRECT_308)
                    .header(HeaderNames.LOCATION, "/lifecycle/final")
                    .send();
        });
        rules.put("/lifecycle/final", (req, res) -> {
            String entity = req.content().as(String.class);
            lifecycleUploadReceived.countDown();
            if (!lifecycleResponseRelease.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release lifecycle response");
            }
            res.send(entity);
        });
        rules.put("/lifecycle/fail-start", (req, res) -> res.status(Status.TEMPORARY_REDIRECT_307)
                .header(HeaderNames.LOCATION, "/lifecycle/fail")
                .send());
        rules.get("/redirect/ftp", (req, res) -> res.status(Status.FOUND_302)
                .header(HeaderNames.LOCATION, "ftp://example.com/file")
                .send());
        rules.put("/cookie/start", (req, res) -> res.status(Status.TEMPORARY_REDIRECT_307)
                .header(HeaderNames.LOCATION, "/cookie/intermediate")
                .send());
        rules.put("/cookie/intermediate", (req, res) -> res.status(Status.TEMPORARY_REDIRECT_307)
                .header(HeaderNames.LOCATION, "/cookie/final")
                .header(HeaderNames.SET_COOKIE, "intermediate=kept; Path=/")
                .send());
        rules.put("/cookie/final", (req, res) -> {
            req.content().as(String.class);
            res.header(HeaderNames.SET_COOKIE, "final=blocked; Path=/")
                    .send("done");
        });
        rules.put("/cookie/loop-start", (req, res) -> res.status(Status.SEE_OTHER_303)
                .header(HeaderNames.LOCATION, "/cookie/loop-intermediate")
                .send());
        rules.get("/cookie/loop-intermediate", (req, res) -> res.status(Status.FOUND_302)
                .header(HeaderNames.LOCATION, "/cookie/loop-final")
                .header(HeaderNames.SET_COOKIE, "loop=kept; Path=/")
                .send());
        rules.get("/cookie/loop-final", (req, res) -> res.send("loop-done"));
        rules.put("/cookie/mixed-start", (req, res) -> res.status(Status.TEMPORARY_REDIRECT_307)
                .header(HeaderNames.LOCATION, "/cookie/mixed-switch")
                .send());
        rules.put("/cookie/mixed-switch", (req, res) -> {
            req.content().as(String.class);
            res.status(Status.SEE_OTHER_303)
                    .header(HeaderNames.LOCATION, "/cookie/mixed-intermediate")
                    .header(HeaderNames.SET_COOKIE, "mixed-switch=kept; Path=/")
                    .send();
        });
        rules.get("/cookie/mixed-intermediate", (req, res) -> res.status(Status.FOUND_302)
                .header(HeaderNames.LOCATION, "/cookie/mixed-final")
                .header(HeaderNames.SET_COOKIE, "mixed-intermediate=kept; Path=/")
                .send());
        rules.get("/cookie/mixed-final", (req, res) -> res.header(HeaderNames.SET_COOKIE,
                                                                 "mixed-final=blocked; Path=/")
                .send("mixed-done"));
        rules.get("/cookie/echo", (req, res) -> res.send(req.headers().contains(HeaderNames.COOKIE)
                                                                 ? String.join("; ",
                                                                               req.headers()
                                                                                       .get(HeaderNames.COOKIE)
                                                                                       .allValues())
                                                                 : "none"));
        rules.get("/afterRedirect", Http1ClientTest::afterRedirectGet);
        rules.put("/afterRedirect", Http1ClientTest::afterRedirectPut);
        rules.put("/chunkresponse", Http1ClientTest::chunkResponseHandler);
        rules.put("/delayedEndpoint", Http1ClientTest::delayedHandler);
    }

    @Test
    void testRequestHeadersUpdated() {
        var client = WebClient.builder()
                .baseUri(baseURI)
                .build();

        HttpClientRequest request = client.get("/test");

        request.request(String.class);

        // this header is computed by Helidon, and would not be present unless the bug 10175 was fixed
        assertThat(request.headers().contentLength(), is(OptionalLong.of(0)));

        client.closeResource();
    }

    @Test
    @SuppressWarnings("deprecation")
    void testMaxHeaderSizeFail() {
        Http1Client client = Http1Client.create(clientConfig -> clientConfig.baseUri(baseURI)
                .protocolConfig(it -> it.maxHeaderSize(15)));

        validateFailedResponse(client, "Header size exceeded");
    }

    @Test
    @SuppressWarnings("deprecation")
    void testMaxHeaderSizeSuccess() {
        Http1Client client = Http1Client.create(clientConfig -> clientConfig.baseUri(baseURI)
                .protocolConfig(it -> it.maxHeaderSize(500)));
        validateSuccessfulResponse(client);
    }

    @Test
    void testMaxStatusLineLengthFail() {
        Http1Client client = Http1Client.create(clientConfig -> clientConfig.baseUri(baseURI)
                .protocolConfig(it -> it.maxStatusLineLength(1)));

        validateFailedResponse(client, "HTTP Response did not contain HTTP status line");
    }

    @Test
    void testMaxHeaderLineLengthSuccess() {
        Http1Client client = Http1Client.create(clientConfig -> clientConfig.baseUri(baseURI)
                .protocolConfig(it -> it.maxStatusLineLength(20)));

        validateSuccessfulResponse(client);
    }

    @Test
    void testMediaContext() {
        Http1Client client = Http1Client.create(clientConfig -> clientConfig.baseUri(baseURI)
                .mediaContext(new CustomizedMediaContext()));

        validateSuccessfulResponse(client);
    }

    @Test
    void testChunk() {
        String[] requestEntityParts = {"First", "Second", "Third"};

        HttpClientRequest request = getHttp1ClientRequest(Method.PUT, "/test");
        HttpClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunkTransfer(response, true, NO_CONTENT_LENGTH, String.join("", requestEntityParts));
    }

    @Test
    void testChunkAndChunkResponse() {
        String[] requestEntityParts = {"First", "Second", "Third"};

        HttpClientRequest request = getHttp1ClientRequest(Method.PUT, "/chunkresponse");
        HttpClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunkTransfer(response, true, NO_CONTENT_LENGTH, String.join("", requestEntityParts));
        assertThat(response.headers(), hasHeader(HeaderValues.TRANSFER_ENCODING_CHUNKED));
    }

    @Test
    void testNoChunk() {
        String[] requestEntityParts = {"First"};
        long contentLength = requestEntityParts[0].length();

        HttpClientRequest request = getHttp1ClientRequest(Method.PUT, "/test")
                .header(HeaderNames.CONTENT_LENGTH, String.valueOf(contentLength));
        HttpClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunkTransfer(response, false, contentLength, requestEntityParts[0]);
    }

    @Test
    void testForcedChunkNoContentLength() {
        String[] requestEntityParts = {"First"};

        HttpClientRequest request = getHttp1ClientRequest(Method.PUT, "/test");
        HttpClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunkTransfer(response, true, NO_CONTENT_LENGTH, requestEntityParts[0]);
    }

    @Test
    void testForcedChunkTransferEncodingChunked() {
        String[] requestEntityParts = {"First"};

        HttpClientRequest request = getHttp1ClientRequest(Method.PUT, "/test")
                .header(HeaderValues.TRANSFER_ENCODING_CHUNKED);
        HttpClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunkTransfer(response, true, NO_CONTENT_LENGTH, requestEntityParts[0]);
    }

    @Test
    void testExpect100() {
        String[] requestEntityParts = {"First", "Second", "Third"};

        WebClient client = WebClient.builder()
                .baseUri(baseURI)
                .sendExpectContinue(true)
                .build();
        HttpClientRequest request = client.put("/test");

        HttpClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunkTransfer(response, true, NO_CONTENT_LENGTH, String.join("", requestEntityParts));
        assertThat(response.headers(), hasHeader(REQ_EXPECT_100_HEADER_NAME));
    }

    // validates that HEAD is not allowed with entity payload
    @Test
    void testHeadMethod() {
        String path = "/test";
        assertThrows(IllegalArgumentException.class, () ->
                injectedHttp1client.head(path).submit("Foo Bar"));
        assertThrows(IllegalArgumentException.class, () ->
                injectedHttp1client.head(path).outputStream(it -> {
                    it.write("Foo Bar".getBytes(StandardCharsets.UTF_8));
                    it.close();
                }));
        injectedHttp1client.head(path).request();
    }

    @Test
    void testConnectionQueueDequeue() {
        ClientConnection connectionNow = null;
        ClientConnection connectionPrior = null;
        for (int i = 0; i < 5; ++i) {
            HttpClientRequest request = injectedHttp1client.put("/test");
            // connection will be dequeued if queue is not empty
            WebClient webClient = WebClient.create();
            Http1ClientConfig clientConfig = Http1ClientConfig.create();
            Http1ClientImpl http1Client = new Http1ClientImpl(webClient, clientConfig);
            connectionNow = http1Client
                    .connectionCache()
                    .connection(http1Client,
                                request,
                                request.resolvedUri(),
                                request.headers(),
                                true);
            request.connection(connectionNow);
            HttpClientResponse response = request.request();
            // connection will be queued up
            response.close();
            if (connectionPrior != null) {
                assertThat(connectionNow, is(connectionPrior));
            }
            connectionPrior = connectionNow;
        }
    }

    @Test
    void testConnectionCachingUnreadEntity() {
        ClientConnection connectionNow;
        ClientConnection connectionPrior = null;
        for (int i = 0; i < 5; ++i) {
            HttpClientRequest request = injectedHttp1client.put("/test");
            // connection will be dequeued if queue is not empty
            WebClient webClient = WebClient.create();
            Http1ClientConfig clientConfig = Http1ClientConfig.create();
            Http1ClientImpl http1Client = new Http1ClientImpl(webClient, clientConfig);
            connectionNow = http1Client
                    .connectionCache()
                    .connection(http1Client,
                                request,
                                request.resolvedUri(),
                                request.headers(),
                                true);
            request.connection(connectionNow);
            // submitted entity is echoed back but not consumed here
            HttpClientResponse response = request.submit("this is an entity");
            // connection will be queued up
            response.close();
            if (connectionPrior != null) {
                assertThat(connectionNow, is(connectionPrior));
            }
            connectionPrior = connectionNow;
        }
    }

    @Test
    void testConnectionQueueSizeLimit() {
        int connectionQueueSize = injectedHttp1client.prototype().connectionCacheSize();

        List<ClientConnection> connectionList = new ArrayList<>();
        List<HttpClientResponse> responseList = new ArrayList<>();
        // create connections beyond the queue size limit
        for (int i = 0; i < connectionQueueSize + 1; ++i) {
            HttpClientRequest request = injectedHttp1client.put("/test");

            WebClient webClient = WebClient.create();
            Http1ClientConfig clientConfig = Http1ClientConfig.create();
            Http1ClientImpl http1Client = new Http1ClientImpl(webClient, clientConfig);

            connectionList.add(http1Client
                                       .connectionCache()
                                       .connection(http1Client,
                                                   request,
                                                   request.resolvedUri(),
                                                   request.headers(),
                                                   true));
            request.connection(connectionList.get(i));
            responseList.add(request.request());
        }

        // Queue up all connections except the last one because it exceeded the queue size limit
        for (HttpClientResponse response : responseList) {
            response.close();
        }

        // dequeue all the created connections
        ClientConnection connection = null;
        HttpClientResponse response = null;
        for (int i = 0; i < connectionQueueSize + 1; ++i) {
            HttpClientRequest request = injectedHttp1client.put("/test");

            WebClient webClient = WebClient.create();
            Http1ClientConfig clientConfig = Http1ClientConfig.create();
            Http1ClientImpl http1Client = new Http1ClientImpl(webClient, clientConfig);

            connection = http1Client
                    .connectionCache()
                    .connection(http1Client,
                                request,
                                request.resolvedUri(),
                                request.headers(),
                                true);

            request.connection(connection);
            response = request.request();
            if (i < connectionQueueSize) {
                // Verify connections that are dequeued
                assertThat("Failed on connection index " + i, connection, is(connectionList.get(i)));
            } else {
                // Verify that the last connection was not dequeued but created as new, because it exceeded the queue size limit
                assertThat(connection, is(not(connectionList.get(i))));
            }
        }

        // The queue is currently empty so check if we can add the last created connection into it.
        response.close();
        HttpClientRequest request = injectedHttp1client.put("/test");

        WebClient webClient = WebClient.create();
        Http1ClientConfig clientConfig = Http1ClientConfig.create();
        Http1ClientImpl http1Client = new Http1ClientImpl(webClient, clientConfig);

        ClientConnection connectionNow = http1Client
                .connectionCache()
                .connection(http1Client,
                            request,
                            request.resolvedUri(),
                            request.headers(),
                            true);

        request.connection(connectionNow);
        HttpClientResponse responseNow = request.request();
        // Verify that the connection was dequeued
        assertThat(connectionNow, is(connection));
    }

    @Test
    void testRedirect() {
        try (HttpClientResponse response = injectedHttp1client.put("/redirect")
                .followRedirects(false)
                .submit("Test entity")) {
            assertThat(response.status(), is(Status.FOUND_302));
        }

        try (HttpClientResponse response = injectedHttp1client.put("/redirect")
                .header(HeaderValues.CONTENT_TYPE_TEXT_PLAIN)
                .header(REQUEST_METADATA_HEADER, "preserved")
                .submit("Test entity")) {
            assertThat(response.status(), is(Status.NO_CONTENT_204));
            assertThat(response.lastEndpointUri().path().path(), is("/afterRedirect"));
        }
    }

    @Test
    void testRedirectKeepMethod() {
        try (HttpClientResponse response = injectedHttp1client.put("/redirectKeepMethod")
                .followRedirects(false)
                .submit("Test entity")) {
            assertThat(response.status(), is(Status.TEMPORARY_REDIRECT_307));
        }

        try (HttpClientResponse response = injectedHttp1client.put("/redirectKeepMethod")
                .header(HeaderValues.CONTENT_TYPE_TEXT_PLAIN)
                .header(REQUEST_METADATA_HEADER, "preserved")
                .submit("Test entity")) {
            assertThat(response.lastEndpointUri().path().path(), is("/afterRedirect"));
            assertThat(response.status(), is(Status.NO_CONTENT_204));
        }
    }

    @Test
    void testSeeOtherRedirectDropsEntityHeadersWithSameMethod() {
        try (HttpClientResponse response = injectedHttp1client.get("/redirectDropEntity")
                .header(HeaderValues.CONTENT_TYPE_TEXT_PLAIN)
                .header(REQUEST_METADATA_HEADER, "preserved")
                .submit("Test entity")) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("GET without entity metadata"));
        }
    }

    @Test
    void testOutputStreamChainedRedirectsWithCustomReasonPhrases() {
        AtomicInteger producerInvocations = new AtomicInteger();
        Http1Client client = Http1Client.builder()
                .baseUri(baseURI)
                .servicesDiscoverServices(false)
                .sendExpectContinue(true)
                .build();
        try (HttpClientResponse response = client.put("/redirectChainStart")
                .header(HeaderValues.CONTENT_TYPE_TEXT_PLAIN)
                .header(REQUEST_METADATA_HEADER, "preserved")
                .outputStream(output -> {
                    producerInvocations.incrementAndGet();
                    output.write("Test entity".getBytes(StandardCharsets.UTF_8));
                    output.close();
                })) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("Test entity"));
            assertThat("HTTP/1 must continue the original producer across redirects", producerInvocations.get(), is(1));
        } finally {
            client.closeResource();
        }
    }

    @ParameterizedTest
    @CsvSource({"301, PUT", "302, PUT", "301, PATCH", "302, PATCH", "301, DELETE", "302, DELETE",
                "301, post", "302, post"})
    void nonPostRedirectPreservesMethodAndEntity(int redirectStatus, String method) {
        Http1Client client = Http1Client.builder()
                .baseUri(baseURI)
                .servicesDiscoverServices(false)
                .build();
        try (HttpClientResponse response = client.method(Method.create(method))
                .uri("/methodRedirect" + redirectStatus)
                .submit("redirect entity")) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat("Redirected method", response.headers(), hasHeader(REDIRECT_METHOD, method));
            assertThat("Redirected entity", response.headers(), hasHeader(REDIRECT_ENTITY, "redirect entity"));
        } finally {
            client.closeResource();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302})
    void headRedirectPreservesMethod(int redirectStatus) {
        Http1Client client = Http1Client.builder()
                .baseUri(baseURI)
                .servicesDiscoverServices(false)
                .build();
        try (HttpClientResponse response = client.head("/methodRedirect" + redirectStatus).request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat("Redirected method", response.headers(), hasHeader(REDIRECT_METHOD, Method.HEAD.text()));
            assertThat("Redirected entity", response.headers(), hasHeader(REDIRECT_ENTITY, ""));
        } finally {
            client.closeResource();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302})
    void postRedirectChangesToGetAndDropsEntity(int redirectStatus) {
        Http1Client client = Http1Client.builder()
                .baseUri(baseURI)
                .servicesDiscoverServices(false)
                .build();
        try (HttpClientResponse response = client.post("/methodRedirect" + redirectStatus).submit("redirect entity")) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat("Redirected method", response.headers(), hasHeader(REDIRECT_METHOD, Method.GET.text()));
            assertThat("Redirected entity", response.headers(), hasHeader(REDIRECT_ENTITY, ""));
        } finally {
            client.closeResource();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302})
    void queryRedirectPreservesBufferedEntity(int redirectStatus) {
        try (HttpClientResponse response = injectedHttp1client.method(Method.QUERY)
                .uri("/queryRedirect" + redirectStatus)
                .header(HeaderNames.CONTENT_TYPE, QUERY_CONTENT_TYPE)
                .submit(QUERY_ENTITY)) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is(Method.QUERY + ":" + QUERY_CONTENT_TYPE + ":" + QUERY_ENTITY));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302})
    void queryRedirectPreservesRequestHeaders(int redirectStatus) {
        try (HttpClientResponse response = injectedHttp1client.method(Method.QUERY)
                .uri("/queryRedirectHeaders" + redirectStatus)
                .header(HeaderNames.ACCEPT, QUERY_ACCEPT)
                .header(HeaderNames.CONTENT_LANGUAGE, QUERY_LANGUAGE)
                .header(HeaderNames.CONTENT_TYPE, QUERY_CONTENT_TYPE)
                .submit(QUERY_ENTITY)) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class),
                       is(Method.QUERY + ":" + QUERY_ACCEPT + ":" + QUERY_LANGUAGE + ":" + QUERY_ENTITY));
        }
    }

    @Test
    void querySubmitUsesMediaWriterContentType() {
        try (HttpClientResponse response = injectedHttp1client.method(Method.QUERY)
                .uri("/queryRedirectTarget")
                .submit(QUERY_ENTITY)) {
            String result = response.as(String.class);
            assertThat(result, startsWith(Method.QUERY + ":text/plain"));
            assertThat(result, containsString(":" + QUERY_ENTITY));
        }
    }

    @Test
    void querySubmitRejectsMissingContentType() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                           () -> injectedHttp1client.method(Method.QUERY)
                                                                   .uri("/queryRedirectTarget")
                                                                   .submit(QUERY_ENTITY.getBytes(StandardCharsets.UTF_8)));

        assertThat(exception.getMessage(), containsString("Content-Type header is required"));
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302})
    void queryRedirectPreservesOutputStreamEntity(int redirectStatus) {
        AtomicInteger producerInvocations = new AtomicInteger();
        Http1Client client = Http1Client.builder()
                .baseUri(baseURI)
                .servicesDiscoverServices(false)
                .sendExpectContinue(true)
                .build();
        try (HttpClientResponse response = client.method(Method.QUERY)
                .uri("/queryRedirect" + redirectStatus)
                .header(HeaderNames.CONTENT_TYPE, QUERY_CONTENT_TYPE)
                .outputStream(output -> {
                    producerInvocations.incrementAndGet();
                    output.write(QUERY_ENTITY.getBytes(StandardCharsets.UTF_8));
                    output.close();
                })) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is(Method.QUERY + ":" + QUERY_CONTENT_TYPE + ":" + QUERY_ENTITY));
            assertThat("HTTP/1 must continue the original QUERY producer after redirect",
                       producerInvocations.get(),
                       is(1));
        } finally {
            client.closeResource();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 307})
    void genericRedirectRejectsClaimedOutputStreamEntity(int redirectStatus) {
        AtomicInteger producerInvocations = new AtomicInteger();
        HttpClientRequest request = injectedHttp1client.method(redirectStatus == 307 ? Method.PUT : Method.QUERY)
                .uri(redirectStatus == 307 ? "/redirectChainStart" : "/queryRedirect" + redirectStatus)
                .header(HeaderNames.CONTENT_TYPE, redirectStatus == 307 ? "text/plain" : QUERY_CONTENT_TYPE)
                .header(REQUEST_METADATA_HEADER, "preserved")
                .sendExpectContinue(true);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                       () -> request.outputStream(output -> {
                                                           producerInvocations.incrementAndGet();
                                                           output.write(QUERY_ENTITY.getBytes(StandardCharsets.UTF_8));
                                                           output.close();
                                                       }));

        assertThat(exception.getMessage(), is("Cannot replay a one-shot request body after redirect status "
                                                     + redirectStatus + "."));
        assertThat("Generic redirects must not invoke a claimed producer again", producerInvocations.get(), is(1));
    }

    @Test
    void querySeeOtherRedirectChangesToGetAndDropsEntity() {
        try (HttpClientResponse response = injectedHttp1client.method(Method.QUERY)
                .uri("/queryRedirect303")
                .header(HeaderNames.CONTENT_TYPE, QUERY_CONTENT_TYPE)
                .submit(QUERY_ENTITY)) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("GET without entity metadata"));
        }
    }

    @Test
    void queryRedirectCompletesEveryServiceRequest() {
        AtomicInteger requests = new AtomicInteger();
        AtomicInteger completions = new AtomicInteger();
        Http1Client client = Http1Client.builder()
                .baseUri(baseURI)
                .servicesDiscoverServices(false)
                .addService((chain, request) -> {
                    requests.incrementAndGet();
                    request.whenComplete().thenRun(completions::incrementAndGet);
                    return chain.proceed(request);
                })
                .build();
        try {
            HttpClientResponse response = client.method(Method.QUERY)
                    .uri("/queryRedirect302")
                    .header(HeaderNames.CONTENT_TYPE, QUERY_CONTENT_TYPE)
                    .submit(QUERY_ENTITY);
            assertThat(requests.get(), is(2));
            assertThat(completions.get(), is(1));
            response.close();
            assertThat(completions.get(), is(2));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void servicedOutputStreamRedirectPreservesEveryHopLifecycleOnCallerThread() throws Exception {
        LifecycleRecorder service = new LifecycleRecorder(null);
        Http1Client client = Http1Client.builder()
                .baseUri(baseURI)
                .servicesDiscoverServices(false)
                .sendExpectContinue(true)
                .readContinueTimeout(Duration.ofMillis(100))
                .addService(service)
                .build();
        lifecycleUploadReceived = new CountDownLatch(1);
        lifecycleResponseRelease = new CountDownLatch(1);

        try {
            CompletableFuture<HttpClientResponse> responseFuture = CompletableFuture.supplyAsync(() -> client
                    .put("/lifecycle/start")
                    .outputStream(output -> {
                        output.write("payload".getBytes(StandardCharsets.UTF_8));
                        assertThat(service.requests().containsKey("/lifecycle/hop"), is(false));
                        output.close();
                    }));
            assertThat(lifecycleUploadReceived.await(5, TimeUnit.SECONDS), is(true));
            RequestLifecycle source = service.requests().get("/lifecycle/start");
            RequestLifecycle target = service.requests().get("/lifecycle/final");
            CompletableFuture.allOf(source.whenSent(), target.whenSent()).get(5, TimeUnit.SECONDS);
            assertThat("source whenSent must complete after the final upload, before response headers",
                       source.whenSent().isDone(),
                       is(true));
            assertThat(target.whenSent().isDone(), is(true));
            assertThat(target.whenComplete().isDone(), is(false));
            assertThat(responseFuture.isDone(), is(false));
            lifecycleResponseRelease.countDown();

            try (HttpClientResponse response = responseFuture.get(5, TimeUnit.SECONDS)) {
                assertThat(response.as(String.class), is("payload"));
            }

            RequestLifecycle hop = service.requests().get("/lifecycle/hop");
            assertThat(hop.responseStatus(), is(Status.PERMANENT_REDIRECT_308));
            assertThat(target.responseStatus(), is(Status.OK_200));
            assertThat(service.requests().keySet(),
                       is(Set.of("/lifecycle/start", "/lifecycle/hop", "/lifecycle/final")));
            assertThat(service.events().indexOf("/lifecycle/start:sent")
                               < service.events().indexOf("/lifecycle/final:response"),
                       is(true));
            for (RequestLifecycle lifecycle : service.requests().values()) {
                assertThat(lifecycle.thread(), is(source.thread()));
                assertThat(lifecycle.whenSent().isDone(), is(true));
                assertThat(lifecycle.whenComplete().isDone(), is(true));
            }
        } finally {
            lifecycleResponseRelease.countDown();
            lifecycleUploadReceived = new CountDownLatch(0);
            lifecycleResponseRelease = new CountDownLatch(0);
            client.closeResource();
        }
    }

    @Test
    void servicedOutputStreamRedirectPropagatesTargetServiceFailure() {
        LifecycleRecorder service = new LifecycleRecorder("/lifecycle/fail");
        Http1Client client = Http1Client.builder()
                .baseUri(baseURI)
                .servicesDiscoverServices(false)
                .sendExpectContinue(true)
                .addService(service)
                .build();

        try {
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> client
                    .put("/lifecycle/fail-start")
                    .outputStream(output -> {
                        output.write("payload".getBytes(StandardCharsets.UTF_8));
                        output.close();
                    }));
            assertThat(failure.getMessage(), is("simulated target service failure"));
            RequestLifecycle target = service.requests().get("/lifecycle/fail");
            assertThat(target.whenSent().isCompletedExceptionally(), is(true));
            assertThat(target.whenComplete().isCompletedExceptionally(), is(true));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void servicedOutputStreamRedirectEnforcesInMemoryLimit() {
        LifecycleRecorder service = new LifecycleRecorder(null);
        Http1Client client = Http1Client.builder()
                .baseUri(baseURI)
                .servicesDiscoverServices(false)
                .sendExpectContinue(true)
                .maxInMemoryEntity(4)
                .addService(service)
                .build();

        try {
            UncheckedIOException failure = assertThrows(UncheckedIOException.class, () -> client
                    .put("/lifecycle/start")
                    .outputStream(output -> output.write("large".getBytes(StandardCharsets.UTF_8))));
            assertThat(failure.getCause().getMessage(), containsString("configured in-memory limit of 4 bytes"));
            assertThat(service.requests().containsKey("/lifecycle/hop"), is(false));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void outerServiceControlsFinalRedirectCookiesWhileIntermediateCookiesAreStored() {
        WebClientCookieManager cookieManager = WebClientCookieManager.create(config -> config.automaticStoreEnabled(true));
        Http1Client client = Http1Client.builder()
                .baseUri(baseURI)
                .servicesDiscoverServices(false)
                .sendExpectContinue(true)
                .cookieManager(cookieManager)
                .addService(new FinalCookieRemovingService())
                .build();

        try {
            try (HttpClientResponse response = client.put("/cookie/start").outputStream(output -> {
                output.write("payload".getBytes(StandardCharsets.UTF_8));
                output.close();
            })) {
                assertThat(response.as(String.class), is("done"));
            }
            try (HttpClientResponse response = client.put("/cookie/loop-start").outputStream(output -> {
                output.write("payload".getBytes(StandardCharsets.UTF_8));
                output.close();
            })) {
                assertThat(response.as(String.class), is("loop-done"));
            }
            try (HttpClientResponse response = client.put("/cookie/mixed-start").outputStream(output -> {
                output.write("payload".getBytes(StandardCharsets.UTF_8));
                output.close();
            })) {
                assertThat(response.as(String.class), is("mixed-done"));
            }
            try (HttpClientResponse response = client.get("/cookie/echo").request()) {
                String cookies = response.as(String.class);
                assertThat(cookies, containsString("intermediate=kept"));
                assertThat(cookies, containsString("loop=kept"));
                assertThat(cookies, containsString("mixed-switch=kept"));
                assertThat(cookies, containsString("mixed-intermediate=kept"));
                assertThat(cookies.contains("final=blocked"), is(false));
                assertThat(cookies.contains("mixed-final=blocked"), is(false));
            }
        } finally {
            client.closeResource();
        }
    }

    @Test
    void rejectsNonHttpRedirectScheme() {
        Http1Client client = Http1Client.builder()
                .baseUri(baseURI)
                .servicesDiscoverServices(false)
                .build();

        try {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                                                             () -> client.get("/redirect/ftp").request());
            assertThat(failure.getMessage(), containsString("Not supported scheme ftp"));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void testReadTimeoutPerRequest() {
        String testEntity = "Test entity";
        try (HttpClientResponse response = injectedHttp1client.put("/delayedEndpoint")
                .submit(testEntity)) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is(testEntity));
        }

        UncheckedIOException ste = assertThrows(UncheckedIOException.class,
                                                () -> injectedHttp1client.put("/delayedEndpoint")
                                                        .readTimeout(Duration.ofMillis(1))
                                                        .submit(testEntity));
        assertThat(ste.getCause(), instanceOf(SocketTimeoutException.class));
    }

    @Test
    void testSchemeValidation() {
        try (var r = Http1Client.builder()
                .baseUri("test://localhost:" + plainPort + "/")
                .shareConnectionCache(false)
                .build()
                .get("/")
                .request()) {

            fail("Should have failed because of invalid scheme.");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), startsWith("Not supported scheme test"));
        }
    }


    private static void validateSuccessfulResponse(Http1Client client) {
        String requestEntity = "Sending Something";
        Http1ClientRequest request = client.put("/test");
        ClientResponseTyped<String> response = request.submit(requestEntity, String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is(requestEntity));
    }

    private static void validateFailedResponse(Http1Client client, String errorMessage) {
        String requestEntity = "Sending Something";
        Http1ClientRequest request = client.put("/test");
        IllegalStateException ie = assertThrows(IllegalStateException.class, () -> request.submit(requestEntity));
        assertThat(ie.getMessage(), containsString(errorMessage));
    }

    private static void validateChunkTransfer(HttpClientResponse response, boolean chunked, long contentLength, String entity) {
        assertThat(response.status(), is(Status.OK_200));
        if (contentLength == NO_CONTENT_LENGTH) {
            assertThat(response.headers(), noHeader(REQ_CONTENT_LENGTH_HEADER_NAME));
        } else {
            assertThat(response.headers(), hasHeader(REQ_CONTENT_LENGTH_HEADER_NAME, String.valueOf(contentLength)));
        }
        if (chunked) {
            assertThat(response.headers(), hasHeader(REQ_CHUNKED_HEADER));
        } else {
            assertThat(response.headers(), noHeader(REQ_CHUNKED_HEADER.headerName()));
        }
        String responseEntity = response.entity().as(String.class);
        assertThat(responseEntity, is(entity));
    }

    private static void redirect(ServerRequest req, ServerResponse res) {
        res.status(Status.FOUND_302)
                .header(HeaderNames.LOCATION, "/afterRedirect")
                .send();
    }

    private static void queryRedirect(ServerResponse response, Status status, String target) {
        response.status(status)
                .header(HeaderNames.LOCATION, target)
                .send();
    }

    private static void queryRedirectTarget(ServerRequest request, ServerResponse response) {
        String contentType = request.headers().get(HeaderNames.CONTENT_TYPE).get();
        response.send(request.prologue().method() + ":" + contentType + ":" + request.content().as(String.class));
    }

    private static void queryRedirectHeadersTarget(ServerRequest request, ServerResponse response) {
        String accept = request.headers().get(HeaderNames.ACCEPT).get();
        String contentLanguage = request.headers().get(HeaderNames.CONTENT_LANGUAGE).get();
        response.send(request.prologue().method()
                              + ":" + accept
                              + ":" + contentLanguage
                              + ":" + request.content().as(String.class));
    }

    private static void queryRedirectGetTarget(ServerRequest request, ServerResponse response) {
        if (request.content().hasEntity() || request.headers().contains(HeaderNames.CONTENT_TYPE)) {
            response.status(Status.BAD_REQUEST_400).send("Entity metadata was preserved");
            return;
        }
        response.send("GET without entity metadata");
    }

    private static void redirectKeepMethod(ServerRequest req, ServerResponse res) {
        res.status(Status.TEMPORARY_REDIRECT_307)
                .header(HeaderNames.LOCATION, "/afterRedirect")
                .send();
    }

    private static void redirectDropEntity(ServerRequest req, ServerResponse res) {
        res.status(Status.SEE_OTHER_303)
                .header(HeaderNames.LOCATION, "/afterDropEntity")
                .send();
    }

    private static void afterDropEntity(ServerRequest req, ServerResponse res) {
        if (req.content().hasEntity()
                || req.headers().contains(HeaderNames.CONTENT_TYPE)) {
            res.status(Status.BAD_REQUEST_400).send("Entity metadata was preserved");
            return;
        }
        if (!req.headers().first(REQUEST_METADATA_HEADER).orElse("").equals("preserved")) {
            res.status(Status.BAD_REQUEST_400).send("Request metadata was not preserved");
            return;
        }
        res.send("GET without entity metadata");
    }

    private static void redirectChainStart(ServerRequest req, ServerResponse res) {
        res.status(Status.create(307, "Custom Temporary Redirect"))
                .header(HeaderNames.LOCATION, "/redirectChainSecond")
                .send();
    }

    private static void redirectChainSecond(ServerRequest req, ServerResponse res) {
        res.status(Status.create(308, "Custom Permanent Redirect"))
                .header(HeaderNames.LOCATION, "/redirectChainFinal")
                .send();
    }

    private static void redirectChainFinal(ServerRequest req, ServerResponse res) {
        if (!req.headers().first(REQUEST_METADATA_HEADER).orElse("").equals("preserved")
                || !req.headers().contains(HeaderValues.CONTENT_TYPE_TEXT_PLAIN)) {
            res.status(Status.BAD_REQUEST_400).send("Unexpected redirected request headers");
            return;
        }
        res.send(req.content().as(String.class));
    }

    private static void afterRedirectGet(ServerRequest req, ServerResponse res) {
        if (req.content().hasEntity()) {
            res.status(Status.BAD_REQUEST_400)
                    .send("GET after redirect endpoint reached with entity");
            return;
        }
        res.send(EXPECTED_GET_AFTER_REDIRECT_STRING);
    }

    private static void afterRedirectPut(ServerRequest req, ServerResponse res) {
        if (!req.headers().first(REQUEST_METADATA_HEADER).orElse("").equals("preserved")
                || !req.headers().contains(HeaderValues.CONTENT_TYPE_TEXT_PLAIN)) {
            res.status(Status.BAD_REQUEST_400).send("Unexpected redirected request headers");
            return;
        }
        String entity = req.content().as(String.class);
        if (!entity.equals("Test entity")) {
            res.status(Status.BAD_REQUEST_400)
                    .send("Entity was not kept the same after the redirect");
            return;
        }
        res.status(Status.NO_CONTENT_204)
                .send();
    }

    private static void delayedHandler(ServerRequest req, ServerResponse res) throws IOException, InterruptedException {
        TimeUnit.SECONDS.sleep(1);
        customHandler(req, res, false);
    }

    private static void responseHandler(ServerRequest req, ServerResponse res) throws IOException {
        customHandler(req, res, false);
    }

    private static void chunkResponseHandler(ServerRequest req, ServerResponse res) throws IOException {
        customHandler(req, res, true);
    }

    private static void customHandler(ServerRequest req, ServerResponse res, boolean chunkResponse) throws IOException {
        Headers reqHeaders = req.headers();
        if (reqHeaders.contains(HeaderValues.EXPECT_100)) {
            res.headers().set(REQ_EXPECT_100_HEADER_NAME);
        }
        if (reqHeaders.contains(HeaderNames.CONTENT_LENGTH)) {
            res.headers().set(REQ_CONTENT_LENGTH_HEADER_NAME, reqHeaders.get(HeaderNames.CONTENT_LENGTH).get());
        }
        if (reqHeaders.contains(HeaderValues.TRANSFER_ENCODING_CHUNKED)) {
            res.headers().set(REQ_CHUNKED_HEADER);
        }

        try (InputStream inputStream = req.content().inputStream();
                OutputStream outputStream = res.outputStream()) {
            if (!chunkResponse) {
                new ByteArrayInputStream(inputStream.readAllBytes()).transferTo(outputStream);
            } else {
                // Break the entity into 3 parts and send them in chunks
                int chunkParts = 3;
                byte[] entity = inputStream.readAllBytes();
                int regularChunkLen = entity.length / chunkParts;
                int lastChunkLen = regularChunkLen + entity.length % chunkParts;
                for (int i = 0; i < chunkParts; i++) {
                    int chunkLen = (i != chunkParts - 1) ? regularChunkLen : lastChunkLen;
                    byte[] chunk = new byte[chunkLen];
                    System.arraycopy(entity, i * regularChunkLen, chunk, 0, chunkLen);
                    outputStream.write(chunk);
                    outputStream.flush();       // will force chunked
                }
            }
        }
    }

    private static HttpClientResponse getHttp1ClientResponseFromOutputStream(HttpClientRequest request,
                                                                             String[] requestEntityParts) {

        return request.outputStream(it -> {
            for (String r : requestEntityParts) {
                it.write(r.getBytes(StandardCharsets.UTF_8));
            }
            it.close();
        });
    }

    private HttpClientRequest getHttp1ClientRequest(Method method, String uriPath) {
        return injectedHttp1client.method(method).uri(uriPath);
    }

    private static final class LifecycleRecorder implements WebClientService {
        private final String failingPath;
        private final Map<String, RequestLifecycle> requests = new ConcurrentHashMap<>();
        private final List<String> events = new CopyOnWriteArrayList<>();

        private LifecycleRecorder(String failingPath) {
            this.failingPath = failingPath;
        }

        @Override
        public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
            String path = request.uri().toUri().getPath();
            RequestLifecycle lifecycle = new RequestLifecycle(request.whenSent().toCompletableFuture(),
                                                              request.whenComplete().toCompletableFuture(),
                                                              Thread.currentThread());
            requests.put(path, lifecycle);
            lifecycle.whenSent().whenComplete((_, _) -> events.add(path + ":sent"));
            lifecycle.whenComplete().whenComplete((_, _) -> events.add(path + ":complete"));
            if (path.equals(failingPath)) {
                throw new IllegalStateException("simulated target service failure");
            }
            WebClientServiceResponse response = chain.proceed(request);
            lifecycle.responseStatus(response.status());
            events.add(path + ":response");
            return response;
        }

        private Map<String, RequestLifecycle> requests() {
            return requests;
        }

        private List<String> events() {
            return events;
        }
    }

    private record FinalCookieRemovingService() implements WebClientService {
        @Override
        public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
            String path = request.uri().toUri().getPath();
            WebClientServiceResponse response = chain.proceed(request);
            if (path.equals("/cookie/intermediate")
                    || path.equals("/cookie/loop-intermediate")
                    || path.equals("/cookie/mixed-switch")
                    || path.equals("/cookie/mixed-intermediate")) {
                request.headers().set(HeaderNames.create("X-Service-Mutated"), "true");
                return response;
            }
            if (!path.equals("/cookie/start") && !path.equals("/cookie/mixed-start")) {
                return response;
            }
            WritableHeaders<?> headers = WritableHeaders.create(response.headers());
            headers.remove(HeaderNames.SET_COOKIE);
            return WebClientServiceResponse.builder(response)
                    .headers(ClientResponseHeaders.create(headers))
                    .build();
        }
    }

    private static final class RequestLifecycle {
        private final CompletableFuture<WebClientServiceRequest> whenSent;
        private final CompletableFuture<WebClientServiceResponse> whenComplete;
        private final Thread thread;
        private volatile Status responseStatus;

        private RequestLifecycle(CompletableFuture<WebClientServiceRequest> whenSent,
                                 CompletableFuture<WebClientServiceResponse> whenComplete,
                                 Thread thread) {
            this.whenSent = whenSent;
            this.whenComplete = whenComplete;
            this.thread = thread;
        }

        private CompletableFuture<WebClientServiceRequest> whenSent() {
            return whenSent;
        }

        private CompletableFuture<WebClientServiceResponse> whenComplete() {
            return whenComplete;
        }

        private Thread thread() {
            return thread;
        }

        private Status responseStatus() {
            return responseStatus;
        }

        private void responseStatus(Status responseStatus) {
            this.responseStatus = responseStatus;
        }
    }

    private static class CustomizedMediaContext implements MediaContext {
        private final MediaContext delegated = MediaContext.create();

        @Override
        public MediaContextConfig prototype() {
            return delegated.prototype();
        }

        @Override
        public <T> EntityReader<T> reader(GenericType<T> type, Headers headers) {
            return delegated.reader(type, headers);
        }

        @Override
        public <T> EntityWriter<T> writer(GenericType<T> type, Headers requestHeaders, WritableHeaders<?> responseHeaders) {
            EntityWriter<T> impl = delegated.writer(type, requestHeaders, responseHeaders);

            EntityWriter<T> realWriter = new EntityWriter<T>() {
                @Override
                public void write(GenericType<T> type,
                                  T object,
                                  OutputStream outputStream,
                                  Headers requestHeaders,
                                  WritableHeaders<?> responseHeaders) {
                    if (object instanceof String) {
                        String maxLen5 = ((String) object).substring(0, 5);
                        impl.write(type, (T) maxLen5, outputStream, requestHeaders, responseHeaders);
                    } else {
                        impl.write(type, object, outputStream, requestHeaders, responseHeaders);
                    }
                }

                @Override
                public void write(GenericType<T> type, T object, OutputStream outputStream, WritableHeaders<?> headers) {
                    impl.write(type, object, outputStream, headers);
                }
            };
            return realWriter;
        }

        @Override
        public <T> EntityReader<T> reader(GenericType<T> type, Headers requestHeaders, Headers responseHeaders) {
            return delegated.reader(type, requestHeaders, responseHeaders);
        }

        @Override
        public <T> EntityWriter<T> writer(GenericType<T> type, WritableHeaders<?> requestHeaders) {
            return delegated.writer(type, requestHeaders);
        }
    }
}
