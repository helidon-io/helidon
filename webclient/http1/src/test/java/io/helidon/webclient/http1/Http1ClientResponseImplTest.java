/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.context.Context;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PeerInfo;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.MediaContext;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.HttpClientConfig;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class Http1ClientResponseImplTest {

    @Test
    void chunkedMustBeFinalTransferCoding() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderValues.create(HeaderNames.TRANSFER_ENCODING, "chunked, gzip"));
        assertThat(Http1CallChainBase.isChunkedFinalTransferCoding(headers), is(false));

        headers = WritableHeaders.create();
        headers.add(HeaderValues.create(HeaderNames.TRANSFER_ENCODING, "gzip, chunked"));
        assertThat(Http1CallChainBase.isChunkedFinalTransferCoding(headers), is(true));
    }

    @Test
    void doesNotCreateEntityStreamForHeadResponseWithoutFraming() {
        WebClientServiceResponse response = serviceResponse(Method.HEAD,
                                                            Status.OK_200,
                                                            ClientResponseHeaders.create(WritableHeaders.create()));

        assertThat(response.inputStream().isEmpty(), is(true));
    }

    @Test
    void doesNotCreateEntityStreamForHeadResponseWithContentLength() {
        WebClientServiceResponse response = serviceResponse(Method.HEAD,
                                                            Status.OK_200,
                                                            contentLengthHeaders());

        assertThat(response.inputStream().isEmpty(), is(true));
    }

    @Test
    void doesNotCreateEntityStreamForNotModifiedWithoutFraming() {
        WebClientServiceResponse response = serviceResponse(Method.GET,
                                                            Status.NOT_MODIFIED_304,
                                                            ClientResponseHeaders.create(WritableHeaders.create()));

        assertThat(response.inputStream().isEmpty(), is(true));
    }

    @Test
    void doesNotCreateEntityStreamForResetContentWithoutFraming() {
        WebClientServiceResponse response = serviceResponse(Method.GET,
                                                            Status.RESET_CONTENT_205,
                                                            ClientResponseHeaders.create(WritableHeaders.create()));

        assertThat(response.inputStream().isEmpty(), is(true));
    }

    @Test
    void createsEntityStreamForUnframedGetResponse() {
        WebClientServiceResponse response = serviceResponse(Method.GET,
                                                            Status.OK_200,
                                                            ClientResponseHeaders.create(WritableHeaders.create()));

        assertThat(response.inputStream().isPresent(), is(true));
    }

    @Test
    void successfulConnectIgnoresFramingHeadersAndClosesTunnel() throws IOException {
        String tunnelData = "4\r\ndata\r\n0\r\n\r\n";
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderValues.TRANSFER_ENCODING_CHUNKED);
        headers.add(HeaderValues.CONTENT_LENGTH_ZERO);
        ClientResponseHeaders responseHeaders = ClientResponseHeaders.create(headers);
        TestServiceRequest serviceRequest = new TestServiceRequest(Method.CONNECT);
        TestConnection connection = new TestConnection(dataReader(tunnelData));
        WebClientServiceResponse serviceResponse = Http1CallChainBase.createServiceResponse(
                new Http1ClientImpl(null, Http1ClientConfig.builder().buildPrototype()),
                serviceRequest,
                connection,
                connection.reader(),
                Status.NO_CONTENT_204,
                responseHeaders,
                new CompletableFuture<>());
        InputStream serviceStream = serviceResponse.inputStream().orElseThrow();
        Http1ClientResponseImpl response = new Http1ClientResponseImpl(HttpClientConfig.builder().build(),
                                                                       Http1ClientProtocolConfig.create(),
                                                                       Status.NO_CONTENT_204,
                                                                       Method.CONNECT,
                                                                       serviceRequest.headers(),
                                                                       responseHeaders,
                                                                       connection,
                                                                       serviceStream,
                                                                       MediaContext.create(),
                                                                       ClientUri.create(URI.create("http://localhost/test")),
                                                                       new CompletableFuture<>());
        InputStream tunnelStream = response.inputStream();

        byte[] actual = new byte[tunnelData.length()];
        assertThat(tunnelStream.read(actual), is(actual.length));
        assertThat(new String(actual, StandardCharsets.US_ASCII), is(tunnelData));
        response.close();
        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
    }

    @Test
    void closesCloseDelimitedResponseAfterEntityRead() {
        TestConnection connection = new TestConnection();
        Http1ClientResponseImpl response = response(ClientResponseHeaders.create(WritableHeaders.create()),
                                                    inputStream("data"),
                                                    connection);

        assertThat(response.entity().as(String.class), is("data"));

        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
    }

    @Test
    void closesUnreadCloseDelimitedResponse() {
        TestConnection connection = new TestConnection();
        Http1ClientResponseImpl response = response(ClientResponseHeaders.create(WritableHeaders.create()),
                                                    inputStream("data"),
                                                    connection);

        response.close();

        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
    }

    @Test
    void releasesNoEntityResponseWithContentLength() {
        TestConnection connection = new TestConnection();
        Http1ClientResponseImpl response = response(contentLengthHeaders(),
                                                    null,
                                                    connection);

        response.close();

        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));
    }

    @Test
    void closesNoEntityResponseWithUnexpectedBufferedData() {
        TestConnection connection = new TestConnection(dataReader("head"));
        Http1ClientResponseImpl response = response(ClientResponseHeaders.create(WritableHeaders.create()),
                                                    null,
                                                    connection);

        response.close();

        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
    }

    @Test
    void releasesLengthDelimitedResponseAfterEntityRead() {
        TestConnection connection = new TestConnection();
        Http1ClientResponseImpl response = response(contentLengthHeaders(),
                                                    inputStream("data"),
                                                    connection);

        assertThat(response.entity().as(String.class), is("data"));

        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));
    }

    private static WebClientServiceResponse serviceResponse(Method method,
                                                            Status status,
                                                            ClientResponseHeaders headers) {
        return Http1CallChainBase.createServiceResponse(new Http1ClientImpl(null,
                                                                            Http1ClientConfig.builder().buildPrototype()),
                                                        new TestServiceRequest(method),
                                                        new TestConnection(),
                                                        DataReader.create(() -> null),
                                                        status,
                                                        headers,
                                                        new CompletableFuture<>());
    }

    private static Http1ClientResponseImpl response(ClientResponseHeaders headers,
                                                    InputStream inputStream,
                                                    TestConnection connection) {
        return new Http1ClientResponseImpl(HttpClientConfig.builder().build(),
                                           Http1ClientProtocolConfig.create(),
                                           Status.OK_200,
                                           Method.GET,
                                           ClientRequestHeaders.create(WritableHeaders.create()),
                                           headers,
                                           connection,
                                           inputStream,
                                           MediaContext.create(),
                                           ClientUri.create(URI.create("http://localhost/test")),
                                           new CompletableFuture<>());
    }

    private static InputStream inputStream(String entity) {
        return new ByteArrayInputStream(entity.getBytes(StandardCharsets.UTF_8));
    }

    private static DataReader dataReader(String entity) {
        AtomicReference<byte[]> bytes = new AtomicReference<>(entity.getBytes(StandardCharsets.UTF_8));
        DataReader reader = DataReader.create(() -> bytes.getAndSet(null));
        reader.pullData();
        return reader;
    }

    private static ClientResponseHeaders contentLengthHeaders() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderNames.CONTENT_LENGTH, "4");
        return ClientResponseHeaders.create(headers);
    }

    private static final class TestServiceRequest implements WebClientServiceRequest {
        private final Method method;
        private final ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        private final Context context = Context.create();
        private final Map<String, String> properties = new HashMap<>();
        private final CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
        private final CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        private String requestId = "test";

        private TestServiceRequest(Method method) {
            this.method = method;
        }

        @Override
        public ClientUri uri() {
            return ClientUri.create(URI.create("http://localhost/test"));
        }

        @Override
        public Method method() {
            return method;
        }

        @Override
        public String protocolId() {
            return "http/1.1";
        }

        @Override
        public ClientRequestHeaders headers() {
            return headers;
        }

        @Override
        public Context context() {
            return context;
        }

        @Override
        public String requestId() {
            return requestId;
        }

        @Override
        public void requestId(String requestId) {
            this.requestId = requestId;
        }

        @Override
        public CompletionStage<WebClientServiceRequest> whenSent() {
            return whenSent;
        }

        @Override
        public CompletionStage<WebClientServiceResponse> whenComplete() {
            return whenComplete;
        }

        @Override
        public Map<String, String> properties() {
            return properties;
        }
    }

    private static final class TestConnection implements ClientConnection {
        private final AtomicInteger releaseCount = new AtomicInteger();
        private final AtomicInteger closeCount = new AtomicInteger();
        private final DataReader reader;

        private TestConnection() {
            this(DataReader.create(() -> null));
        }

        private TestConnection(DataReader reader) {
            this.reader = reader;
        }

        @Override
        public DataReader reader() {
            return reader;
        }

        @Override
        public DataWriter writer() {
            return new NoopDataWriter();
        }

        @Override
        public String channelId() {
            return "test";
        }

        @Override
        public HelidonSocket helidonSocket() {
            return new TestSocket();
        }

        @Override
        public void readTimeout(Duration readTimeout) {
        }

        @Override
        public void releaseResource() {
            releaseCount.incrementAndGet();
        }

        @Override
        public void closeResource() {
            closeCount.incrementAndGet();
        }

        private int releaseCount() {
            return releaseCount.get();
        }

        private int closeCount() {
            return closeCount.get();
        }
    }

    private static final class NoopDataWriter implements DataWriter {
        @Override
        public void write(BufferData... buffers) {
        }

        @Override
        public void write(BufferData buffer) {
        }

        @Override
        public void writeNow(BufferData... buffers) {
        }

        @Override
        public void writeNow(BufferData buffer) {
        }
    }

    private static final class TestSocket implements HelidonSocket {
        @Override
        public void close() {
        }

        @Override
        public void idle() {
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public void write(BufferData buffer) {
        }

        @Override
        public PeerInfo remotePeer() {
            return null;
        }

        @Override
        public PeerInfo localPeer() {
            return null;
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public String socketId() {
            return "test";
        }

        @Override
        public String childSocketId() {
            return "test";
        }

        @Override
        public byte[] get() {
            return new byte[0];
        }
    }
}
