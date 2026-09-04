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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.context.Context;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.spi.WebClientService;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http1CallEntityChainTest {

    @Test
    void entityWriteFailureCompletesWhenSentExceptionally() throws Exception {
        IllegalStateException expected = new IllegalStateException("expected entity write failure");
        Http1Client client = Http1Client.create();
        try {
            FailingEntityRequest request = new FailingEntityRequest((Http1ClientImpl) client, expected);

            IllegalStateException actual = assertThrows(
                    IllegalStateException.class,
                    () -> request.invokeRequestWithEntity("payload".getBytes(StandardCharsets.US_ASCII)));

            assertThat(actual, sameInstance(expected));
            assertThat(request.entityReachedWriter(), is(true));
            ExecutionException sentFailure = assertThrows(
                    ExecutionException.class,
                    () -> request.serviceRequest().whenSent().toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertThat(sentFailure.getCause(), sameInstance(expected));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void responseReadFailurePreservesDirectLifecycleCause() throws Exception {
        IOException expected = new IOException("simulated HTTP/1 response read failure");
        Http1Client client = Http1Client.create();
        try {
            LifecycleFailureRequest request = new LifecycleFailureRequest((Http1ClientImpl) client, expected);
            Http1ClientResponseImpl response = request.invokeRequestWithEntity(
                    "payload".getBytes(StandardCharsets.US_ASCII));

            UncheckedIOException actual = assertThrows(UncheckedIOException.class,
                                                       () -> response.entity().as(byte[].class));

            ExecutionException lifecycleFailure = assertThrows(
                    ExecutionException.class,
                    () -> request.serviceRequest().whenComplete().toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertThat(actual.getCause(), sameInstance(expected));
            assertThat(lifecycleFailure.getCause(), sameInstance(actual));
        } finally {
            client.closeResource();
        }
    }

    private static final class FailingEntityRequest extends Http1ClientRequestImpl {
        private final IllegalStateException expected;
        private TestServiceRequest serviceRequest;
        private boolean entityReachedWriter;

        private FailingEntityRequest(Http1ClientImpl client, IllegalStateException expected) {
            super(client,
                  null,
                  Method.POST,
                  ClientUri.create(URI.create("http://localhost/entity")),
                  null,
                  Map.of());
            this.expected = expected;
        }

        @Override
        protected WebClientServiceResponse invokeServices(WebClientService.WireProtocolChain httpCallChain,
                                                          CompletableFuture<WebClientServiceRequest> whenSent,
                                                          CompletableFuture<WebClientServiceResponse> whenComplete,
                                                          ClientUri usedUri,
                                                          Consumer<WebClientServiceRequest> requestPrepare) {
            ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
            serviceRequest = new TestServiceRequest(usedUri, headers, whenSent, whenComplete);
            requestPrepare.accept(serviceRequest);
            DataWriter writer = new DataWriter() {
                @Override
                public void write(BufferData... buffers) {
                    throw new AssertionError("Unexpected multi-buffer write");
                }

                @Override
                public void write(BufferData buffer) {
                    BufferData copy = buffer.copy();
                    byte[] bytes = new byte[copy.available()];
                    copy.read(bytes);
                    entityReachedWriter = new String(bytes, StandardCharsets.US_ASCII).endsWith("payload");
                    throw expected;
                }

                @Override
                public void writeNow(BufferData... buffers) {
                    throw new AssertionError("Unexpected immediate multi-buffer write");
                }

                @Override
                public void writeNow(BufferData buffer) {
                    throw new AssertionError("Unexpected immediate write");
                }
            };

            return ((Http1CallEntityChain) httpCallChain).doProceed(new TestConnection(writer),
                                                                    serviceRequest,
                                                                    headers,
                                                                    writer,
                                                                    DataReader.create(() -> null),
                                                                    BufferData.growing(64));
        }

        private TestServiceRequest serviceRequest() {
            return serviceRequest;
        }

        private boolean entityReachedWriter() {
            return entityReachedWriter;
        }
    }

    private static final class LifecycleFailureRequest extends Http1ClientRequestImpl {
        private final IOException expected;
        private final TestConnection connection = new TestConnection(new NoopDataWriter());
        private WebClientServiceRequest serviceRequest;

        private LifecycleFailureRequest(Http1ClientImpl client, IOException expected) {
            super(client,
                  null,
                  Method.POST,
                  ClientUri.create(URI.create("http://localhost/entity")),
                  null,
                  Map.of());
            this.expected = expected;
        }

        @Override
        protected WebClientServiceResponse invokeServices(WebClientService.WireProtocolChain httpCallChain,
                                                          CompletableFuture<WebClientServiceRequest> whenSent,
                                                          CompletableFuture<WebClientServiceResponse> whenComplete,
                                                          ClientUri usedUri,
                                                          Consumer<WebClientServiceRequest> requestPrepare) {
            WebClientService.WireProtocolChain replacement = new WebClientService.WireProtocolChain() {
                @Override
                public WebClientServiceResponse proceed(WebClientServiceRequest request) {
                    serviceRequest = request;
                    whenSent.complete(request);
                    InputStream inputStream = new InputStream() {
                        @Override
                        public int read() throws IOException {
                            throw expected;
                        }
                    };
                    return WebClientServiceResponse.builder()
                            .serviceRequest(request)
                            .whenComplete(whenComplete)
                            .connection(connection)
                            .status(Status.OK_200)
                            .headers(ClientResponseHeaders.create(WritableHeaders.create()))
                            .inputStream(inputStream)
                            .build();
                }

                @Override
                public String protocolId() {
                    return httpCallChain.protocolId();
                }
            };
            return super.invokeServices(replacement,
                                        whenSent,
                                        whenComplete,
                                        usedUri,
                                        requestPrepare);
        }

        private WebClientServiceRequest serviceRequest() {
            return serviceRequest;
        }
    }

    private static final class TestConnection implements ClientConnection {
        private final DataWriter writer;

        private TestConnection(DataWriter writer) {
            this.writer = writer;
        }

        @Override
        public DataReader reader() {
            return DataReader.create(() -> null);
        }

        @Override
        public DataWriter writer() {
            return writer;
        }

        @Override
        public String channelId() {
            return "test";
        }

        @Override
        public HelidonSocket helidonSocket() {
            return null;
        }

        @Override
        public void readTimeout(Duration readTimeout) {
        }

        @Override
        public void closeResource() {
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

    private static final class TestServiceRequest implements WebClientServiceRequest {
        private final ClientUri uri;
        private final ClientRequestHeaders headers;
        private final CompletableFuture<WebClientServiceRequest> whenSent;
        private final CompletableFuture<WebClientServiceResponse> whenComplete;
        private final Map<String, String> properties = new HashMap<>();
        private String requestId = "test";

        private TestServiceRequest(ClientUri uri,
                                   ClientRequestHeaders headers,
                                   CompletableFuture<WebClientServiceRequest> whenSent,
                                   CompletableFuture<WebClientServiceResponse> whenComplete) {
            this.uri = uri;
            this.headers = headers;
            this.whenSent = whenSent;
            this.whenComplete = whenComplete;
        }

        @Override
        public ClientUri uri() {
            return uri;
        }

        @Override
        public Method method() {
            return Method.POST;
        }

        @Override
        public String protocolId() {
            return Http1Client.PROTOCOL_ID;
        }

        @Override
        public ClientRequestHeaders headers() {
            return headers;
        }

        @Override
        public Context context() {
            return Context.create();
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
}
