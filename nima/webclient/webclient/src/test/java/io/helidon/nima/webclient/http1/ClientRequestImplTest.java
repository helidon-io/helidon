/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import io.helidon.common.GenericType;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.WritableHeaders;
import io.helidon.nima.http.media.EntityReader;
import io.helidon.nima.http.media.EntityWriter;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.webclient.WebClient;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientRequestImplTest {
    private static final Http.HeaderValue REQ_CHUNKED_HEADER = Http.Header.createCached(Http.Header.create("X-Req-Chunked"),
                                                                                        "true");
    private static final Http.HeaderValue REQ_EXPECT_100_HEADER_NAME = Http.Header.createCached(Http.Header.create(
            "X-Req-Expect100"), "true");
    private static final Http.HeaderName REQ_CONTENT_LENGTH_HEADER_NAME = Http.Header.create("X-Req-ContentLength");
    private final static long NO_CONTENT_LENGTH = -1L;
    private static WebServer webServer;

    @BeforeAll
    static void beforeAll() {
        webServer = WebServer.builder()
                .routing(router -> router
                        .put("/test", ClientRequestImplTest::responseHandler)
                        .put("/chunkresponse", ClientRequestImplTest::chunkResponseHandler)
                )
                .build()
                .start();
    }

    @AfterAll
    static void afterAll() {
        if (webServer != null) {
            webServer.stop();
        }
    }

    @Test
    void testMaxHeaderSizeFail() {
        Http1Client client = WebClient.builder()
                .maxHeaderSize(10)
                .build();
        validateFailedResponse(client, "Header size exceeded");
    }

    @Test
    void testMaxHeaderSizeSuccess() {
        Http1Client client = WebClient.builder()
                .maxHeaderSize(500)
                .build();
        validateSuccessfulResponse(client);
    }

    @Test
    void testMaxStatusLineLengthFail() {
        Http1Client client = WebClient.builder()
                .maxStatusLineLength(1)
                .build();
        validateFailedResponse(client, "HTTP Response did not contain HTTP status line");
    }

    @Test
    void testMaxHeaderLineLengthSuccess() {
        Http1Client client = WebClient.builder()
                .maxStatusLineLength(20)
                .build();
        validateSuccessfulResponse(client);
    }

    @Test
    void testMediaContext() {
        Http1Client client = WebClient.builder()
                .mediaContext(new CustomizedMediaContext())
                .build();
        validateSuccessfulResponse(client);
    }

    @Test
    void testChunk() {
        String requestEntityParts[] = {"First", "Second", "Third"};

        Http1ClientRequest request = getHttp1ClientRequest(Http.Method.PUT, "/test");
        Http1ClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunking(response, true, NO_CONTENT_LENGTH, String.join("", requestEntityParts));
    }

    @Test
    void testChunkAndChunkResponse() {
        String requestEntityParts[] = {"First", "Second", "Third"};

        Http1ClientRequest request = getHttp1ClientRequest(Http.Method.PUT, "/chunkresponse");
        Http1ClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunking(response, true, NO_CONTENT_LENGTH, String.join("", requestEntityParts));
        assertThat(response.headers().contains(Http.HeaderValues.TRANSFER_ENCODING_CHUNKED), is(true));
    }

    @Test
    void testNoChunk() {
        String requestEntityParts[] = {"First"};
        long contentLength = requestEntityParts[0].length();

        Http1ClientRequest request = getHttp1ClientRequest(Http.Method.PUT, "/test")
                .header(Http.Header.CONTENT_LENGTH, String.valueOf(contentLength));
        Http1ClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunking(response, false, contentLength, requestEntityParts[0]);
    }

    @Test
    void testForcedChunkNoContentLength() {
        String requestEntityParts[] = {"First"};

        Http1ClientRequest request = getHttp1ClientRequest(Http.Method.PUT, "/test");
        Http1ClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunking(response, true, NO_CONTENT_LENGTH, requestEntityParts[0]);
    }

    @Test
    void testForcedChunkTransferEncodingChunked() {
        String requestEntityParts[] = {"First"};

        Http1ClientRequest request = getHttp1ClientRequest(Http.Method.PUT, "/test")
                .header(Http.HeaderValues.TRANSFER_ENCODING_CHUNKED);
        Http1ClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunking(response, true, NO_CONTENT_LENGTH, requestEntityParts[0]);
    }

    @Test
    void testExpect100() {
        String requestEntityParts[] = {"First", "Second", "Third"};
        int port = webServer.port();

        Http1Client client = WebClient.builder()
                .sendExpect100Continue(true)
                .build();
        Http1ClientRequest request = client.method(Http.Method.PUT)
                .uri("http://localhost:" + port + "/test");

        Http1ClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunking(response, true, NO_CONTENT_LENGTH, String.join("", requestEntityParts));
        assertThat(response.headers().contains(REQ_EXPECT_100_HEADER_NAME), is(true));
    }

    private static void validateSuccessfulResponse(Http1Client client) {
        String requestEntity = "Sending Something";
        Http1ClientRequest request = client.method(Http.Method.PUT).path("http://localhost:" + webServer.port() + "/test");
        Http1ClientResponse response = request.submit(requestEntity);

        assertThat(response.status(), is(Http.Status.OK_200));
        assertThat(response.entity().as(String.class), is(requestEntity));
    }

    private static void validateFailedResponse(Http1Client client, String errorMessage) {
        String requestEntity = "Sending Something";
        Http1ClientRequest request = client.method(Http.Method.PUT).path("http://localhost:" + webServer.port() + "/test");
        final IllegalStateException ie =
                assertThrows(IllegalStateException.class, () -> request.submit(requestEntity));
        assertThat(ie.getMessage().contains(errorMessage), is(true));
    }

    private static void responseHandler(ServerRequest req, ServerResponse res) {
        customHandler(req, res, false);
    }

    private static void chunkResponseHandler(ServerRequest req, ServerResponse res) {
        customHandler(req, res, true);
    }

    private static void customHandler(ServerRequest req, ServerResponse res, boolean chunkResponse) {
        Headers reqHeaders = req.headers();
        if (reqHeaders.contains(Http.HeaderValues.EXPECT_100)) {
            res.headers().add(REQ_EXPECT_100_HEADER_NAME);
        }
        res.headers().add(REQ_CONTENT_LENGTH_HEADER_NAME, String.valueOf(reqHeaders.contentLength().orElse(NO_CONTENT_LENGTH)));
        if (reqHeaders.contains(Http.HeaderValues.TRANSFER_ENCODING_CHUNKED)) {
            res.headers().add(REQ_CHUNKED_HEADER);
        }

        try (InputStream inputStream = req.content().inputStream();
                OutputStream outputStream = res.outputStream()) {
            if (!chunkResponse) {
                inputStream.transferTo(outputStream);
            } else {
                // Break the entity into 3 parts and send them in chunks
                int chunkParts = 3;
                byte[] entity = inputStream.readAllBytes();
                int regularChunkLen = entity.length / chunkParts;
                int lastChunkLen = regularChunkLen + entity.length % chunkParts;
                for (int i = 0; i < chunkParts; i++) {
                    int chunkLen = i != (chunkParts - 1) ? regularChunkLen : lastChunkLen;
                    byte[] chunk = new byte[chunkLen];
                    System.arraycopy(entity, i * regularChunkLen, chunk, 0, chunkLen);
                    outputStream.write(chunk);
                }
            }
        } catch (Exception e) {
            res.status(Http.Status.INTERNAL_SERVER_ERROR_500).send("failed: " + e.getMessage());
        }
    }

    private static Http1ClientRequest getHttp1ClientRequest(Http.Method method, String uriPath) {
        Http1Client client = WebClient.builder()
                .sendExpect100Continue(true)
                .build();
        Http1ClientRequest request = client.method(method)
                .uri("http://localhost:" + webServer.port() + uriPath);
        return request;
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

    private void validateChunking(Http1ClientResponse response, boolean chunked, long contentLength, String entity) {
        assertThat(response.status(), is(Http.Status.OK_200));
        assertThat(Long.parseLong(response.headers().get(REQ_CONTENT_LENGTH_HEADER_NAME).value()), is(contentLength));
        assertThat(response.headers().contains(REQ_CHUNKED_HEADER), is(chunked));
        String responseEntity = response.entity().as(String.class);
        assertThat(responseEntity, is(entity));
    }

    private static class CustomizedMediaContext implements MediaContext {
        private MediaContext delegated = MediaContext.create();

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
