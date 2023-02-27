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

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.Bytes;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http1HeadersParser;
import io.helidon.common.http.WritableHeaders;
import io.helidon.nima.http.media.EntityReader;
import io.helidon.nima.http.media.EntityWriter;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.webclient.ClientConnection;
import io.helidon.nima.webclient.WebClient;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.noHeader;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientRequestImplTest {
    private static final Http.HeaderValue REQ_CHUNKED_HEADER = Http.Header.createCached(
            Http.Header.create("X-Req-Chunked"), "true");
    private static final Http.HeaderValue REQ_EXPECT_100_HEADER_NAME = Http.Header.createCached(
            Http.Header.create("X-Req-Expect100"), "true");
    private static final Http.HeaderName REQ_CONTENT_LENGTH_HEADER_NAME = Http.Header.create("X-Req-ContentLength");
    private static final long NO_CONTENT_LENGTH = -1L;
    private static final Http1Client client = WebClient.builder().build();
    private static final int dummyPort = 1234;

    void testMaxHeaderSizeFail() {
        Http1Client client = WebClient.builder()
                .maxHeaderSize(15)
                .build();
        validateFailedResponse(client, new FakeHttp1ClientConnection(), "Header size exceeded");
    }

    void testMaxHeaderSizeSuccess() {
        Http1Client client = WebClient.builder()
                .maxHeaderSize(500)
                .build();
        validateSuccessfulResponse(client, new FakeHttp1ClientConnection());
    }

    void testMaxStatusLineLengthFail() {
        Http1Client client = WebClient.builder()
                .maxStatusLineLength(1)
                .build();
        validateFailedResponse(client, new FakeHttp1ClientConnection(), "HTTP Response did not contain HTTP status line");
    }

    void testMaxHeaderLineLengthSuccess() {
        Http1Client client = WebClient.builder()
                .maxStatusLineLength(20)
                .build();
        validateSuccessfulResponse(client, new FakeHttp1ClientConnection());
    }

    @Test
    void testMediaContext() {
        Http1Client client = WebClient.builder()
                .mediaContext(new CustomizedMediaContext())
                .build();
        validateSuccessfulResponse(client, new FakeHttp1ClientConnection());
    }

    @Test
    void testChunk() {
        String[] requestEntityParts = {"First", "Second", "Third"};

        Http1ClientRequest request = getHttp1ClientRequest(Http.Method.PUT, "/test");
        request.connection(new FakeHttp1ClientConnection());
        Http1ClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunkTransfer(response, true, NO_CONTENT_LENGTH, String.join("", requestEntityParts));
    }

    @Test
    void testNoChunk() {
        String[] requestEntityParts = {"First"};
        long contentLength = requestEntityParts[0].length();

        Http1ClientRequest request = getHttp1ClientRequest(Http.Method.PUT, "/test")
                .header(Http.Header.CONTENT_LENGTH, String.valueOf(contentLength));
        request.connection(new FakeHttp1ClientConnection());
        Http1ClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunkTransfer(response, false, contentLength, requestEntityParts[0]);
    }

    @Test
    void testForcedChunkNoContentLength() {
        String[] requestEntityParts = {"First"};

        Http1ClientRequest request = getHttp1ClientRequest(Http.Method.PUT, "/test");
        request.connection(new FakeHttp1ClientConnection());
        Http1ClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunkTransfer(response, true, NO_CONTENT_LENGTH, requestEntityParts[0]);
    }

    @Test
    void testForcedChunkTransferEncodingChunked() {
        String[] requestEntityParts = {"First"};

        Http1ClientRequest request = getHttp1ClientRequest(Http.Method.PUT, "/test")
                .header(Http.HeaderValues.TRANSFER_ENCODING_CHUNKED);
        request.connection(new FakeHttp1ClientConnection());
        Http1ClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunkTransfer(response, true, NO_CONTENT_LENGTH, requestEntityParts[0]);
    }

    void testExpect100() {
        String[] requestEntityParts = {"First", "Second", "Third"};

        Http1Client client = WebClient.builder()
                .sendExpect100Continue(true)
                .build();
        Http1ClientRequest request = client.method(Http.Method.PUT)
                .uri("http://localhost:" + dummyPort + "/test");
        request.connection(new FakeHttp1ClientConnection());

        Http1ClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunkTransfer(response, true, NO_CONTENT_LENGTH, String.join("", requestEntityParts));
        assertThat(response.headers(), hasHeader(REQ_EXPECT_100_HEADER_NAME));
    }

    // validates that HEAD is not allowed with entity payload
    void testHeadMethod() {
        String url = "http://localhost:" + dummyPort + "/test";
        ClientConnection http1ClientConnection = new FakeHttp1ClientConnection();
        assertThrows(IllegalArgumentException.class, () ->
                client.method(Http.Method.HEAD).uri(url).connection(http1ClientConnection).submit("Foo Bar"));
        assertThrows(IllegalArgumentException.class, () ->
                client.method(Http.Method.HEAD).uri(url).connection(http1ClientConnection).outputStream(it -> {
                    it.write("Foo Bar".getBytes(StandardCharsets.UTF_8));
                    it.close();
                }));
        client.method(Http.Method.HEAD).uri(url).connection(http1ClientConnection).request();
        http1ClientConnection.close();
    }

    private static void validateSuccessfulResponse(Http1Client client, ClientConnection connection) {
        String requestEntity = "Sending Something";
        Http1ClientRequest request = client.method(Http.Method.PUT).path("http://localhost:" + dummyPort + "/test");
        if (connection != null) {
            request.connection(connection);
        }
        Http1ClientResponse response = request.submit(requestEntity);

        assertThat(response.status(), is(Http.Status.OK_200));
        assertThat(response.entity().as(String.class), is(requestEntity));
    }

    private static void validateFailedResponse(Http1Client client, ClientConnection connection, String errorMessage) {
        String requestEntity = "Sending Something";
        Http1ClientRequest request = client.method(Http.Method.PUT).path("http://localhost:" + dummyPort + "/test");
        if (connection != null) {
            request.connection(connection);
        }
        IllegalStateException ie = assertThrows(IllegalStateException.class, () -> request.submit(requestEntity));
        if (errorMessage != null) {
            assertThat(ie.getMessage(), containsString(errorMessage));
        }
    }

    private void validateChunkTransfer(Http1ClientResponse response, boolean chunked, long contentLength, String entity) {
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

    private static Http1ClientRequest getHttp1ClientRequest(Http.Method method, String uriPath) {
        return client.method(method).uri("http://localhost:" + dummyPort + uriPath);
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

    private static class FakeHttp1ClientConnection implements ClientConnection {
        private final DataReader clientReader;
        private final DataWriter clientWriter;
        private final DataReader serverReader;
        private final DataWriter serverWriter;
        private Throwable serverException;
        private ExecutorService webServerEmulator;

        FakeHttp1ClientConnection() {
            ArrayBlockingQueue<byte[]> serverToClient = new ArrayBlockingQueue<>(1024);
            ArrayBlockingQueue<byte[]> clientToServer = new ArrayBlockingQueue<>(1024);

            this.clientReader = reader(serverToClient);
            this.clientWriter = writer(clientToServer);
            this.serverReader = reader(clientToServer);
            this.serverWriter = writer(serverToClient);
        }

        @Override
        public DataReader reader() {
            return clientReader;
        }

        @Override
        public DataWriter writer() {
            return clientWriter;
        }

        @Override
        public void release() {
        }

        @Override
        public void close() {
            webServerEmulator.shutdownNow();
        }

        @Override
        public String channelId() {
            return null;
        }

        private DataWriter writer(ArrayBlockingQueue<byte[]> queue) {
            return new DataWriter() {
                @Override
                public void write(BufferData... buffers) {
                    writeNow(buffers);
                }

                @Override
                public void write(BufferData buffer) {
                    writeNow(buffer);
                }

                @Override
                public void writeNow(BufferData... buffers) {
                    for (BufferData buffer : buffers) {
                        writeNow(buffer);
                    }
                }

                @Override
                public void writeNow(BufferData buffer) {
                    if (serverException != null) {
                        throw new IllegalStateException("Server exception", serverException);
                    }
                    if (webServerEmulator == null) {
                        webServerEmulator = Executors.newFixedThreadPool(1);
                        webServerEmulator.submit(() -> {
                            try {
                                webServerHandle();
                            } catch (Throwable t) {
                                serverException = t;
                                throw t;
                            }
                        });
                    }

                    byte[] bytes = new byte[buffer.available()];
                    buffer.read(bytes);
                    try {
                        queue.put(bytes);
                    } catch (InterruptedException e) {
                        throw new IllegalStateException("Thread interrupted", e);
                    }
                }
            };
        }

        private DataReader reader(ArrayBlockingQueue<byte[]> queue) {
            return new DataReader(() -> {
                if (serverException != null) {
                    throw new IllegalStateException("Server exception", serverException);
                }
                byte[] data;
                try {
                    data = queue.take();
                } catch (InterruptedException e) {
                    throw new IllegalArgumentException("Thread interrupted", e);
                }
                if (data.length == 0) {
                    return null;
                }
                return data;
            });
        }

        private void webServerHandle() {
            BufferData entity = BufferData.growing(128);

            // Read prologue
            int lineLength = serverReader.findNewLine(16384);
            if (lineLength > 0) {
                //String prologue = serverReader.readAsciiString(lineLength);
                serverReader.skip(lineLength + 2); // skip Prologue + CRLF
            }

            // Read Headers
            WritableHeaders<?> reqHeaders = Http1HeadersParser.readHeaders(serverReader, 16384, true);

            int entitySize = 0;
            if (reqHeaders.contains(Http.HeaderValues.TRANSFER_ENCODING_CHUNKED)) {
                // Send 100-Continue if requested
                if (reqHeaders.contains(Http.HeaderValues.EXPECT_100)) {
                    serverWriter.write(
                            BufferData.create("HTTP/1.1 100 Continue\r\n".getBytes(StandardCharsets.UTF_8)));
                }

                // Assemble the entity from the chunks
                while (true) {
                    String hex = serverReader.readLine();
                    int chunkLength = Integer.parseUnsignedInt(hex, 16);
                    if (chunkLength == 0) {
                        serverReader.readLine();
                        break;
                    }
                    BufferData chunkData = serverReader.readBuffer(chunkLength);
                    entity.write(chunkData);
                    serverReader.skip(2);
                    entitySize += chunkLength;
                }
            } else if (reqHeaders.contains(Http.Header.CONTENT_LENGTH)) {
                entitySize = reqHeaders.get(Http.Header.CONTENT_LENGTH).value(int.class);
                if (entitySize > 0) {
                    entity.write(serverReader.getBuffer(entitySize));
                }
            }

            WritableHeaders<?> resHeaders = WritableHeaders.create();
            resHeaders.add(Http.HeaderValues.CONNECTION_KEEP_ALIVE);

            // Send headers that can be validated if Expect-100-Continue, Content_Length, and Chunked request headers exist
            if (reqHeaders.contains(Http.HeaderValues.EXPECT_100)) {
                resHeaders.set(REQ_EXPECT_100_HEADER_NAME);
            }
            if (reqHeaders.contains(Http.Header.CONTENT_LENGTH)) {
                resHeaders.set(REQ_CONTENT_LENGTH_HEADER_NAME, reqHeaders.get(Http.Header.CONTENT_LENGTH).value());
            }
            if (reqHeaders.contains(Http.HeaderValues.TRANSFER_ENCODING_CHUNKED)) {
                resHeaders.set(REQ_CHUNKED_HEADER);
            }

            // Send OK status response
            serverWriter.write(BufferData.create("HTTP/1.1 200 OK\r\n".getBytes(StandardCharsets.UTF_8)));

            // Send the headers
            resHeaders.add(Http.Header.CONTENT_LENGTH, Integer.toString(entitySize));
            BufferData entityBuffer = BufferData.growing(128);
            for (Http.HeaderValue header : resHeaders) {
                header.writeHttp1Header(entityBuffer);
            }
            entityBuffer.write(Bytes.CR_BYTE);
            entityBuffer.write(Bytes.LF_BYTE);
            serverWriter.write(entityBuffer);

            // Send the entity if it exist
            if (entitySize > 0) {
                serverWriter.write(entity);
            }
        }
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
