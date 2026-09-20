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
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.api.ClientAltSvcConfig;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientConnectionTarget;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.TcpClientConnection;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.WebClientProtocolResponse;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.spi.WebClientService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http1CallEntityChainTest {

    @ParameterizedTest
    @CsvSource({"true, 200", "true, 307", "false, 200", "false, 307"})
    void entityResponseCapturesAltSvcOnce(boolean redirectProbe, int statusCode) {
        try (var fixture = new ResponseFixture(redirectProbe, true, true)) {
            WebClientServiceResponse response = fixture.proceed("HTTP/1.1 103 Early Hints\r\n"
                                                                       + "Alt-Svc: h3=\":9443\"\r\n\r\n"
                                                                       + finalResponse(statusCode, true));

            assertThat(response.status().code(), is(statusCode));
            assertThat(fixture.whenSent.getNow(null), sameInstance(fixture.serviceRequest));
            Optional<WebClientProtocolResponse> captured = fixture.chain.protocolResponse(response);
            assertThat("The final response must be available to protocol discovery", captured.isPresent(), is(true));
            WebClientProtocolResponse protocolResponse = captured.orElseThrow();
            assertThat(protocolResponse.target(), sameInstance(fixture.connection.resolvedTarget().orElseThrow()));
            assertThat(protocolResponse.explicitConnection(), is(false));
            assertThat(protocolResponse.protocolId(), is(Http1Client.PROTOCOL_ID));
            assertThat(protocolResponse.status().code(), is(statusCode));
            assertThat(protocolResponse.headers().get(HeaderNames.ALT_SVC).get(), is("h3=\":8443\""));
            assertThat("Protocol discovery must receive each response only once",
                       fixture.chain.protocolResponse(response).isEmpty(),
                       is(true));
        }
    }

    @ParameterizedTest
    @CsvSource({"false, true, true", "true, false, true", "true, true, false"})
    void redirectProbeRequiresEligibleAltSvc(boolean altSvcEnabled, boolean altSvcPresent, boolean resolvedTarget) {
        try (var fixture = new ResponseFixture(true, altSvcEnabled, resolvedTarget)) {
            WebClientServiceResponse response = fixture.proceed("HTTP/1.1 103 Early Hints\r\n"
                                                                       + "Alt-Svc: h3=\":9443\"\r\n\r\n"
                                                                       + finalResponse(307, altSvcPresent));

            assertThat(response.status(), is(Status.TEMPORARY_REDIRECT_307));
            assertThat("Disabled discovery, absent final Alt-Svc, or an unresolved target must not be captured",
                       fixture.chain.protocolResponse(response).isEmpty(),
                       is(true));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void entityResponsePreservesContinueHandling(boolean redirectProbe) {
        try (var fixture = new ResponseFixture(redirectProbe, true, true)) {
            WebClientServiceResponse response = fixture.proceed("HTTP/1.1 103 Early Hints\r\n\r\n"
                                                                       + "HTTP/1.1 102 Processing\r\n\r\n"
                                                                       + "HTTP/1.1 100 Continue\r\n\r\n"
                                                                       + finalResponse(200, true));

            assertThat(response.status(), is(redirectProbe ? Status.CONTINUE_100 : Status.OK_200));
            if (redirectProbe) {
                assertThat(fixture.chain.protocolResponse(response).isEmpty(), is(true));
                // A redirect probe returns at 100 Continue so its caller can send the entity before reading the final head.
                response = fixture.chain.readResponse(fixture.serviceRequest, fixture.connection, fixture.reader);
                assertThat(response.status(), is(Status.OK_200));
            }
            Optional<WebClientProtocolResponse> captured = fixture.chain.protocolResponse(response);
            assertThat("The final response must remain available after informational responses", captured.isPresent(), is(true));
            assertThat(captured.orElseThrow().status(), is(Status.OK_200));
            assertThat(fixture.chain.protocolResponse(response).isEmpty(), is(true));
        }
    }

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

    private static String finalResponse(int statusCode, boolean altSvcPresent) {
        return "HTTP/1.1 " + statusCode + " " + Status.create(statusCode).reasonPhrase() + "\r\n"
                + (altSvcPresent ? "Alt-Svc: h3=\":8443\"\r\n" : "")
                + (statusCode == 307 ? "Location: /redirected\r\n" : "")
                + "Content-Length: 0\r\n\r\n";
    }

    private static final class ResponseFixture implements AutoCloseable {
        private final Http1ClientImpl client;
        private final TcpClientConnection connection;
        private final CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
        private final TestServiceRequest serviceRequest;
        private final Http1CallEntityChain chain;
        private DataReader reader;

        private ResponseFixture(boolean redirectProbe, boolean altSvcEnabled, boolean resolvedTarget) {
            client = (Http1ClientImpl) Http1Client.builder()
                    .altSvc(ClientAltSvcConfig.builder().enabled(altSvcEnabled).build())
                    .proxy(Proxy.noProxy())
                    .protocolConfig(config -> config.log(log -> log.receiveLog(false).sendLog(false)))
                    .build();
            var uri = ClientUri.create(URI.create("http://127.0.0.1/entity"));
            var headers = ClientRequestHeaders.create(WritableHeaders.create());
            var whenComplete = new CompletableFuture<WebClientServiceResponse>();
            var request = new Http1ClientRequestImpl(client, null, Method.POST, uri, null, Map.of())
                    .outputStreamRedirect(redirectProbe);
            var key = Http1ConnectionCache.connectionKey(request, uri, headers, client.clientConfig());
            connection = resolvedTarget
                    ? TcpClientConnection.create(client.webClient(),
                                                 ClientConnectionTarget.create(key, uri.scheme()).resolve(),
                                                 List.of(Http1Client.PROTOCOL_ID),
                                                 _ -> false,
                                                 _ -> { })
                    : TcpClientConnection.create(client.webClient(), key, List.of(Http1Client.PROTOCOL_ID), _ -> false, _ -> { });
            serviceRequest = new TestServiceRequest(uri, headers, whenSent, whenComplete);
            chain = new Http1CallEntityChain(client, request, whenSent, whenComplete, new byte[0]);
        }

        @Override
        public void close() {
            try {
                connection.closeResource();
            } finally {
                client.closeResource();
            }
        }

        private WebClientServiceResponse proceed(String wireResponse) {
            var chunks = List.of(wireResponse.getBytes(StandardCharsets.US_ASCII)).iterator();
            reader = DataReader.create(() -> chunks.hasNext() ? chunks.next() : null);
            return chain.doProceed(connection,
                                   serviceRequest,
                                   serviceRequest.headers(),
                                   new NoopDataWriter(),
                                   reader,
                                   BufferData.growing(64));
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
        protected WebClientServiceResponse invokeServices(WebClient webClient,
                                                          WebClientService.TransportChain httpCallChain,
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
        protected WebClientServiceResponse invokeServices(WebClient webClient,
                                                          WebClientService.TransportChain httpCallChain,
                                                          CompletableFuture<WebClientServiceRequest> whenSent,
                                                          CompletableFuture<WebClientServiceResponse> whenComplete,
                                                          ClientUri usedUri,
                                                          Consumer<WebClientServiceRequest> requestPrepare) {
            WebClientService.TransportChain replacement = new WebClientService.TransportChain() {
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

                @Override
                public Optional<WebClientProtocolResponse> protocolResponse(WebClientServiceResponse response) {
                    return Optional.empty();
                }
            };
            return super.invokeServices(webClient,
                                        replacement,
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
