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

package io.helidon.nima.webclient.http1;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;

import io.helidon.common.GenericType;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.WritableHeaders;
import io.helidon.nima.http.media.EntityReader;
import io.helidon.nima.http.media.EntityWriter;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.http.media.MediaContextConfig;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webclient.ClientConnection;
import io.helidon.nima.webclient.WebClient;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.noHeader;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ServerTest
class ClientRequestImplTest {
    private static final Http.HeaderValue REQ_CHUNKED_HEADER = Http.Header.createCached(
            Http.Header.create("X-Req-Chunked"), "true");
    private static final Http.HeaderValue REQ_EXPECT_100_HEADER_NAME = Http.Header.createCached(
            Http.Header.create("X-Req-Expect100"), "true");
    private static final Http.HeaderName REQ_CONTENT_LENGTH_HEADER_NAME = Http.Header.create("X-Req-ContentLength");
    private static final String EXPECTED_GET_AFTER_REDIRECT_STRING = "GET after redirect endpoint reached";
    private static final long NO_CONTENT_LENGTH = -1L;

    private final String baseURI;
    private final Http1Client injectedHttp1client;

    ClientRequestImplTest(WebServer webServer, Http1Client client) {
        baseURI = "http://localhost:" + webServer.port();
        injectedHttp1client = client;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.put("/test", ClientRequestImplTest::responseHandler);
        rules.put("/redirectKeepMethod", ClientRequestImplTest::redirectKeepMethod);
        rules.put("/redirect", ClientRequestImplTest::redirect);
        rules.get("/afterRedirect", ClientRequestImplTest::afterRedirectGet);
        rules.put("/afterRedirect", ClientRequestImplTest::afterRedirectPut);
        rules.put("/chunkresponse", ClientRequestImplTest::chunkResponseHandler);
    }

    @Test
    void testMaxHeaderSizeFail() {
        Http1Client client = WebClient.builder()
                .baseUri(baseURI)
                .maxHeaderSize(15)
                .build();
        validateFailedResponse(client, "Header size exceeded");
    }

    @Test
    void testMaxHeaderSizeSuccess() {
        Http1Client client = WebClient.builder()
                .baseUri(baseURI)
                .maxHeaderSize(500)
                .build();
        validateSuccessfulResponse(client);
    }

    @Test
    void testMaxStatusLineLengthFail() {
        Http1Client client = WebClient.builder()
                .baseUri(baseURI)
                .maxStatusLineLength(1)
                .build();
        validateFailedResponse(client, "HTTP Response did not contain HTTP status line");
    }

    @Test
    void testMaxHeaderLineLengthSuccess() {
        Http1Client client = WebClient.builder()
                .baseUri(baseURI)
                .maxStatusLineLength(20)
                .build();
        validateSuccessfulResponse(client);
    }

    @Test
    void testMediaContext() {
        Http1Client client = WebClient.builder()
                .baseUri(baseURI)
                .mediaContext(new CustomizedMediaContext())
                .build();
        validateSuccessfulResponse(client);
    }

    @Test
    void testChunk() {
        String[] requestEntityParts = {"First", "Second", "Third"};

        Http1ClientRequest request = getHttp1ClientRequest(Http.Method.PUT, "/test");
        Http1ClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunkTransfer(response, true, NO_CONTENT_LENGTH, String.join("", requestEntityParts));
    }

    @Test
    void testChunkAndChunkResponse() {
        String[] requestEntityParts = {"First", "Second", "Third"};

        Http1ClientRequest request = getHttp1ClientRequest(Http.Method.PUT, "/chunkresponse");
        Http1ClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunkTransfer(response, true, NO_CONTENT_LENGTH, String.join("", requestEntityParts));
        assertThat(response.headers(), hasHeader(Http.HeaderValues.TRANSFER_ENCODING_CHUNKED));
    }

    @Test
    void testNoChunk() {
        String[] requestEntityParts = {"First"};
        long contentLength = requestEntityParts[0].length();

        Http1ClientRequest request = getHttp1ClientRequest(Http.Method.PUT, "/test")
                .header(Http.Header.CONTENT_LENGTH, String.valueOf(contentLength));
        Http1ClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunkTransfer(response, false, contentLength, requestEntityParts[0]);
    }

    @Test
    void testForcedChunkNoContentLength() {
        String[] requestEntityParts = {"First"};

        Http1ClientRequest request = getHttp1ClientRequest(Http.Method.PUT, "/test");
        Http1ClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunkTransfer(response, true, NO_CONTENT_LENGTH, requestEntityParts[0]);
    }

    @Test
    void testForcedChunkTransferEncodingChunked() {
        String[] requestEntityParts = {"First"};

        Http1ClientRequest request = getHttp1ClientRequest(Http.Method.PUT, "/test")
                .header(Http.HeaderValues.TRANSFER_ENCODING_CHUNKED);
        Http1ClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunkTransfer(response, true, NO_CONTENT_LENGTH, requestEntityParts[0]);
    }

    @Test
    void testExpect100() {
        String[] requestEntityParts = {"First", "Second", "Third"};

        Http1Client client = WebClient.builder()
                .baseUri(baseURI)
                .sendExpect100Continue(true)
                .build();
        Http1ClientRequest request = client.put("/test");

        Http1ClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

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
            Http1ClientRequest request = injectedHttp1client.put("/test");
            // connection will be dequeued if queue is not empty
            ClientRequestImpl requestImpl = (ClientRequestImpl) request;
            connectionNow = ConnectionCache.connection(requestImpl.clientConfig(),
                                                       null,
                                                       requestImpl.uri(),
                                                       requestImpl.headers());
            request.connection(connectionNow);
            Http1ClientResponse response = request.request();
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
        int connectionQueueSize = ((Http1ClientImpl) injectedHttp1client).clientConfig().connectionQueueSize();

        List<ClientConnection> connectionList = new ArrayList<ClientConnection>();
        List<Http1ClientResponse> responseList = new ArrayList<Http1ClientResponse>();
        // create connections beyond the queue size limit
        for (int i = 0; i < connectionQueueSize + 1; ++i) {
            Http1ClientRequest request = injectedHttp1client.put("/test");
            ClientRequestImpl requestImpl = (ClientRequestImpl) request;
            connectionList.add(ConnectionCache.connection(requestImpl.clientConfig(),
                                                       null,
                                                       requestImpl.uri(),
                                                       requestImpl.headers()));
            request.connection(connectionList.get(i));
            responseList.add(request.request());
        }

        // Queue up all connections except the last one because it exceeded the queue size limit
        for (Http1ClientResponse response : responseList) {
            response.close();
        }

        // dequeue all the created connections
        ClientConnection connection = null;
        Http1ClientResponse response = null;
        for (int i = 0; i < connectionQueueSize + 1; ++i) {
            Http1ClientRequest request = injectedHttp1client.put("/test");
            ClientRequestImpl requestImpl = (ClientRequestImpl) request;
            connection = ConnectionCache.connection(requestImpl.clientConfig(),
                                                          null,
                                                          requestImpl.uri(),
                                                          requestImpl.headers());
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
        Http1ClientRequest request = injectedHttp1client.put("/test");
        ClientRequestImpl requestImpl = (ClientRequestImpl) request;
        ClientConnection connectionNow = ConnectionCache.connection(requestImpl.clientConfig(),
                                                null,
                                                requestImpl.uri(),
                                                requestImpl.headers());
        request.connection(connectionNow);
        Http1ClientResponse responseNow = request.request();
        // Verify that the connection was dequeued
        assertThat(connectionNow, is(connection));
    }

    @Test
    void testRedirect() {
        try (Http1ClientResponse response = injectedHttp1client.put("/redirect")
                .followRedirects(false)
                .submit("Test entity")) {
            assertThat(response.status(), is(Http.Status.FOUND_302));
        }

        try (Http1ClientResponse response = injectedHttp1client.put("/redirect")
                .submit("Test entity")) {
            assertThat(response.status(), is(Http.Status.OK_200));
            assertThat(response.as(String.class), is(EXPECTED_GET_AFTER_REDIRECT_STRING));
        }
    }

    @Test
    void testRedirectKeepMethod() {
        try (Http1ClientResponse response = injectedHttp1client.put("/redirectKeepMethod")
                .followRedirects(false)
                .submit("Test entity")) {
            assertThat(response.status(), is(Http.Status.TEMPORARY_REDIRECT_307));
        }

        try (Http1ClientResponse response = injectedHttp1client.put("/redirectKeepMethod")
                .submit("Test entity")) {
            assertThat(response.status(), is(Http.Status.NO_CONTENT_204));
        }
    }

    private static void validateSuccessfulResponse(Http1Client client) {
        String requestEntity = "Sending Something";
        Http1ClientRequest request = client.put("/test");
        Http1ClientResponse response = request.submit(requestEntity);

        assertThat(response.status(), is(Http.Status.OK_200));
        assertThat(response.entity().as(String.class), is(requestEntity));
    }

    private static void validateFailedResponse(Http1Client client, String errorMessage) {
        String requestEntity = "Sending Something";
        Http1ClientRequest request = client.put("/test");
        IllegalStateException ie = assertThrows(IllegalStateException.class, () -> request.submit(requestEntity));
        assertThat(ie.getMessage(), containsString(errorMessage));
    }

    private static void validateChunkTransfer(Http1ClientResponse response, boolean chunked, long contentLength, String entity) {
        assertThat(response.status(), is(Http.Status.OK_200));
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
        res.status(Http.Status.FOUND_302)
                .header(Http.Header.LOCATION, "/afterRedirect")
                .send();
    }

    private static void redirectKeepMethod(ServerRequest req, ServerResponse res) {
        res.status(Http.Status.TEMPORARY_REDIRECT_307)
                .header(Http.Header.LOCATION, "/afterRedirect")
                .send();
    }

    private static void afterRedirectGet(ServerRequest req, ServerResponse res) {
        if (req.content().hasEntity()) {
            res.status(Http.Status.BAD_REQUEST_400)
                    .send("GET after redirect endpoint reached with entity");
            return;
        }
        res.send(EXPECTED_GET_AFTER_REDIRECT_STRING);
    }

    private static void afterRedirectPut(ServerRequest req, ServerResponse res) {
        String entity = req.content().as(String.class);
        if (!entity.equals("Test entity")) {
            res.status(Http.Status.BAD_REQUEST_400)
                    .send("Entity was not kept the same after the redirect");
            return;
        }
        res.status(Http.Status.NO_CONTENT_204)
                .send();
    }

    private static void responseHandler(ServerRequest req, ServerResponse res) throws IOException {
        customHandler(req, res, false);
    }

    private static void chunkResponseHandler(ServerRequest req, ServerResponse res) throws IOException {
        customHandler(req, res, true);
    }

    private static void customHandler(ServerRequest req, ServerResponse res, boolean chunkResponse) throws IOException {
        Headers reqHeaders = req.headers();
        if (reqHeaders.contains(Http.HeaderValues.EXPECT_100)) {
            res.headers().set(REQ_EXPECT_100_HEADER_NAME);
        }
        if (reqHeaders.contains(Http.Header.CONTENT_LENGTH)) {
            res.headers().set(REQ_CONTENT_LENGTH_HEADER_NAME, reqHeaders.get(Http.Header.CONTENT_LENGTH).value());
        }
        if (reqHeaders.contains(Http.HeaderValues.TRANSFER_ENCODING_CHUNKED)) {
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

    private Http1ClientRequest getHttp1ClientRequest(Http.Method method, String uriPath) {
        return injectedHttp1client.method(method).uri(uriPath);
    }

    private static Http1ClientResponse getHttp1ClientResponseFromOutputStream(Http1ClientRequest request,
                                                                              String[] requestEntityParts) {
        Http1ClientResponse response = request.outputStream(it -> {
            for (String r : requestEntityParts) {
                it.write(r.getBytes(StandardCharsets.UTF_8));
            }
            it.close();
        });
        return response;
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
