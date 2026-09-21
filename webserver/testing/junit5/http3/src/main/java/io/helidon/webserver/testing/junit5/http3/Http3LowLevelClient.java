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

package io.helidon.webserver.testing.junit5.http3;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.Api;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.tls.Tls;
import io.helidon.http.Header;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http3.Http3ControlStreamListener;
import io.helidon.http.http3.Http3ControlStreamSupport;
import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.http.http3.Http3GoAway;
import io.helidon.http.http3.Http3PeerCriticalStreams;
import io.helidon.http.http3.Http3Protocol;
import io.helidon.http.http3.Http3ProtocolException;
import io.helidon.http.http3.Http3QpackContext;
import io.helidon.http.http3.Http3Settings;
import io.helidon.http.http3.Http3StreamObservation;
import io.helidon.http.http3.Http3StreamSupport;
import io.helidon.http.http3.Http3StreamType;
import io.helidon.quic.QuicClientConnection;
import io.helidon.quic.QuicClientRuntime;
import io.helidon.quic.QuicCloseCommand;
import io.helidon.quic.QuicConfig;
import io.helidon.quic.QuicConnection;
import io.helidon.quic.QuicRemoteStreamRegistration;
import io.helidon.quic.QuicVersion;
import io.helidon.quic.SequentialScheduler;
import io.helidon.quic.VariableLengthEncoder;
import io.helidon.quic.stream.QuicBidiStream;
import io.helidon.quic.stream.QuicReceiverStream;
import io.helidon.quic.stream.QuicSenderStream;
import io.helidon.quic.stream.QuicStreamReader;
import io.helidon.quic.stream.QuicStreamWriter;
import io.helidon.webclient.http3.Http3Client;

/**
 * Low-level HTTP/3 test client that keeps connection-scoped QPACK state visible to assertions.
 */
@Api.Incubating
public final class Http3LowLevelClient implements AutoCloseable {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final long LOCAL_QPACK_MAX_TABLE_CAPACITY = 4096;
    private static final int LOCAL_QPACK_BLOCKED_STREAMS = 16;

    private final URI baseUri;
    private final ExecutorService executor;
    private final QuicClientRuntime client;
    private final QuicClientConnection connection;
    private final Http3QpackContext qpackContext;
    private final Http3PeerCriticalStreams peerCriticalStreams;
    private final QuicRemoteStreamRegistration remoteStreamRegistration;
    private final ConcurrentHashMap<Long, Http3StreamObservation> observations;
    private final AtomicBoolean closed;

    private Http3LowLevelClient(URI baseUri,
                                ExecutorService executor,
                                QuicClientRuntime client,
                                QuicClientConnection connection,
                                Http3QpackContext qpackContext,
                                Http3PeerCriticalStreams peerCriticalStreams,
                                QuicRemoteStreamRegistration remoteStreamRegistration,
                                ConcurrentHashMap<Long, Http3StreamObservation> observations,
                                AtomicBoolean closed) {
        this.baseUri = baseUri;
        this.executor = executor;
        this.client = client;
        this.connection = connection;
        this.qpackContext = qpackContext;
        this.peerCriticalStreams = peerCriticalStreams;
        this.remoteStreamRegistration = remoteStreamRegistration;
        this.observations = observations;
        this.closed = closed;
    }

    /**
     * Create a low-level HTTP/3 test client for the provided base URI and TLS configuration.
     *
     * @param baseUri base server URI
     * @param tls client TLS configuration
     * @return initialized low-level client
     */
    public static Http3LowLevelClient create(URI baseUri, Tls tls) {
        Objects.requireNonNull(baseUri);
        Objects.requireNonNull(tls);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        QuicClientRuntime client = null;
        QuicRemoteStreamRegistration registration = null;
        ConcurrentHashMap<Long, Http3StreamObservation> observations = new ConcurrentHashMap<>();
        AtomicBoolean closed = new AtomicBoolean();
        Http3PeerCriticalStreams peerCriticalStreams = Http3PeerCriticalStreams.create();
        try {
            client = QuicClientRuntime.builder()
                    .executor(executor)
                    .quicConfig(QuicConfig.builder()
                                        .availableVersions(List.of(QuicVersion.QUIC_V1))
                                        .buildPrototype())
                    .tls(tls)
                    .build();
            InetSocketAddress peerAddress =
                    new InetSocketAddress(InetAddress.getByName(baseUri.getHost()), port(baseUri));
            QuicClientConnection connection = client.createConnection(peerAddress,
                                                                      peerAddress.getHostString(),
                                                                      peerAddress.getPort(),
                                                                      new String[] {Http3Client.PROTOCOL_ID});
            Http3QpackContext qpackContext = Http3QpackContext.create(LOCAL_QPACK_MAX_TABLE_CAPACITY,
                                                                      LOCAL_QPACK_BLOCKED_STREAMS,
                                                                      16_384,
                                                                      throwable -> terminateConnection(connection,
                                                                                                       throwable));
            registration = connection.addRemoteStreamListener(stream -> {
                if (stream instanceof QuicReceiverStream receiver && !(stream instanceof QuicBidiStream)) {
                    Http3StreamObservation observation = Http3ControlStreamSupport.observe(
                            receiver,
                            qpackContext,
                            peerCriticalStreams,
                            connection,
                            controlStreamListener(qpackContext));
                    observations.put(stream.streamId(), observation);
                    observation.completion().whenComplete((ignored, throwable) -> {
                        observations.remove(stream.streamId(), observation);
                        if (throwable != null && !closed.get()) {
                            terminateConnection(connection, throwable);
                        }
                    });
                    if (closed.get() && observations.remove(stream.streamId(), observation)) {
                        observation.close();
                    }
                    return true;
                }
                return false;
            });
            await(connection.startHandshake(), 20, TimeUnit.SECONDS, "completing the HTTP/3 handshake");
            primeControlStreams(connection, qpackContext);
            return new Http3LowLevelClient(baseUri,
                                           executor,
                                           client,
                                           connection,
                                           qpackContext,
                                           peerCriticalStreams,
                                           registration,
                                           observations,
                                           closed);
        } catch (Exception e) {
            closed.set(true);
            if (registration != null) {
                try {
                    registration.close();
                } catch (Throwable cleanupFailure) {
                    e.addSuppressed(cleanupFailure);
                }
            }
            for (Http3StreamObservation observation : List.copyOf(observations.values())) {
                try {
                    observation.close();
                } catch (Throwable cleanupFailure) {
                    e.addSuppressed(cleanupFailure);
                }
            }
            IllegalStateException closeCause = new IllegalStateException("HTTP/3 low-level client initialization failed");
            try {
                peerCriticalStreams.close(closeCause);
            } catch (Throwable cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            if (client != null) {
                try {
                    client.close();
                } catch (Throwable cleanupFailure) {
                    e.addSuppressed(cleanupFailure);
                }
            }
            try {
                executor.close();
            } catch (Throwable cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw new IllegalStateException("Failed to initialize low-level HTTP/3 test client.", e);
        }
    }

    /**
     * Base URI used by this client.
     *
     * @return base URI
     */
    public URI baseUri() {
        return baseUri;
    }

    /**
     * Issue a GET request against the provided path relative to the base URI.
     *
     * @param path request path
     * @return decoded low-level response
     */
    public DecodedResponse get(String path) {
        return get(baseUri.resolve(path));
    }

    /**
     * Issue a GET request against the provided URI.
     *
     * @param uri request URI
     * @return decoded low-level response
     */
    public DecodedResponse get(URI uri) {
        return request(uri, "GET", WritableHeaders.create());
    }

    /**
     * Issue a request and decode the raw HTTP/3 frames through the live per-connection QPACK state.
     *
     * @param uri request URI
     * @param method HTTP method
     * @param headers request headers
     * @return decoded low-level response
     */
    public DecodedResponse request(URI uri, String method, Headers headers) {
        Objects.requireNonNull(uri);
        Objects.requireNonNull(method);
        Objects.requireNonNull(headers);

        return request(connection, qpackContext, uri, method, headers);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        Throwable failure = null;
        try {
            remoteStreamRegistration.close();
        } catch (Throwable cleanupFailure) {
            failure = cleanupFailure;
        }
        for (Http3StreamObservation observation : List.copyOf(observations.values())) {
            try {
                observation.close();
            } catch (Throwable cleanupFailure) {
                failure = collectCleanupFailure(failure, cleanupFailure);
            }
        }
        observations.clear();
        IllegalStateException closeCause = new IllegalStateException("HTTP/3 low-level client is closed");
        try {
            qpackContext.close(closeCause);
        } catch (Throwable cleanupFailure) {
            failure = collectCleanupFailure(failure, cleanupFailure);
        }
        try {
            peerCriticalStreams.close(closeCause);
        } catch (Throwable cleanupFailure) {
            failure = collectCleanupFailure(failure, cleanupFailure);
        }
        try {
            client.close();
        } catch (Throwable cleanupFailure) {
            failure = collectCleanupFailure(failure, cleanupFailure);
        }
        try {
            executor.close();
        } catch (Throwable cleanupFailure) {
            failure = collectCleanupFailure(failure, cleanupFailure);
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure != null) {
            throw new IllegalStateException("Failed to close HTTP/3 low-level client", failure);
        }
    }

    private static Throwable collectCleanupFailure(Throwable current, Throwable next) {
        if (current == null) {
            return next;
        }
        if (current != next) {
            current.addSuppressed(next);
        }
        return current;
    }

    private static DecodedResponse request(QuicConnection connection,
                                           Http3QpackContext qpackContext,
                                           URI uri,
                                           String method,
                                           Headers headers) {
        RequestStream requestStream = openRequestStream(connection);
        CompletableFuture<byte[]> responseFuture = readAll(requestStream.stream());
        requestStream.writer()
                .scheduleForWriting(BufferData.create(encodeRequestHeaders(qpackContext,
                                                                          requestStream.stream().streamId(),
                                                                          uri,
                                                                          method,
                                                                          headers)),
                                    true);
        byte[] response = await(responseFuture,
                                10,
                                TimeUnit.SECONDS,
                                "reading the HTTP/3 response from " + uri);
        return decodeResponse(qpackContext, requestStream.stream().streamId(), response);
    }

    private static int port(URI baseUri) {
        return baseUri.getPort() > 0 ? baseUri.getPort() : 443;
    }

    private static CompletableFuture<byte[]> readAll(QuicReceiverStream stream) {
        CompletableFuture<byte[]> result = new CompletableFuture<>();
        BufferData output = BufferData.growing(256);
        QuicStreamReader[] holder = new QuicStreamReader[1];
        SequentialScheduler scheduler = SequentialScheduler.lockingScheduler(() -> {
            try {
                QuicStreamReader reader = holder[0];
                for (;;) {
                    Optional<BufferData> next = reader.poll();
                    if (next.isEmpty()) {
                        return;
                    }
                    BufferData buffer = next.orElseThrow();
                    if (buffer == QuicStreamReader.EOF) {
                        result.complete(output.readBytes());
                        return;
                    }
                    output.write(buffer);
                }
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        holder[0] = stream.connectReader(scheduler);
        holder[0].start();
        return result;
    }

    private static DecodedResponseHead decodeResponseHead(Http3QpackContext qpackContext,
                                                          long streamId,
                                                          byte[] headersPayload) {
        Http3QpackContext.Stream qpackStream = qpackContext.openStream(streamId);
        try {
            Headers decodedHeaders = qpackStream.decodeHeaders(BufferData.create(headersPayload), -1);
            int status = -1;
            WritableHeaders<?> headers = WritableHeaders.create();
            for (Header header : decodedHeaders) {
                if (header.headerName().lowerCase().equals(":status")) {
                    status = Integer.parseInt(header.get());
                } else {
                    headers.add(header);
                }
            }
            if (status < 0) {
                throw new IllegalArgumentException("Missing :status pseudo-header");
            }
            return new DecodedResponseHead(status, headers);
        } finally {
            qpackStream.complete();
        }
    }

    private static DecodedResponse decodeResponse(Http3QpackContext qpackContext, long streamId, byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long frameType = VariableLengthEncoder.decode(buffer);
        long frameLength = VariableLengthEncoder.decode(buffer);
        if (frameType != Http3Protocol.FRAME_HEADERS || frameLength < 0 || frameLength > buffer.remaining()) {
            throw new IllegalStateException("Malformed HTTP/3 response message.");
        }
        byte[] headersPayload = new byte[(int) frameLength];
        buffer.get(headersPayload);
        DecodedResponseHead responseHead = decodeResponseHead(qpackContext, streamId, headersPayload);
        BufferData body = BufferData.growing(256);
        while (buffer.hasRemaining()) {
            long nextType = VariableLengthEncoder.decode(buffer);
            long nextLength = VariableLengthEncoder.decode(buffer);
            if (nextLength < 0 || nextLength > buffer.remaining()) {
                throw new IllegalStateException("Malformed HTTP/3 response frame.");
            }
            byte[] payload = new byte[(int) nextLength];
            buffer.get(payload);
            if (nextType == Http3Protocol.FRAME_DATA) {
                body.write(payload);
            }
        }
        return DecodedResponse.create(responseHead.status(),
                                      responseHead.headers(),
                                      headersPayload.length,
                                      body.readBytes());
    }

    private static byte[] encodeRequestHeaders(Http3QpackContext qpackContext,
                                               long streamId,
                                               URI uri,
                                               String method,
                                               Headers headers) {
        return Http3Protocol.encodeRequestHeaders(qpackContext, streamId, uri, method, headers);
    }

    private static void primeControlStreams(QuicConnection connection,
                                            Http3QpackContext qpackContext) {
        openAndPrimeUniStream(connection,
                              Http3Protocol.controlStreamPreamble(
                                      Http3Settings.create(LOCAL_QPACK_MAX_TABLE_CAPACITY,
                                                           LOCAL_QPACK_BLOCKED_STREAMS)),
                              Http3StreamType.CONTROL);
        QuicStreamWriter encoderWriter = openAndPrimeUniStream(connection,
                                                               Http3Protocol.qpackUniStreamPreamble(
                                                                       Http3StreamType.QPACK_ENCODER),
                                                               Http3StreamType.QPACK_ENCODER);
        qpackContext.encoderInstructionsSender(bytes -> encoderWriter.scheduleForWriting(BufferData.create(bytes), false));
        QuicStreamWriter decoderWriter = openAndPrimeUniStream(connection,
                                                               Http3Protocol.qpackUniStreamPreamble(
                                                                       Http3StreamType.QPACK_DECODER),
                                                               Http3StreamType.QPACK_DECODER);
        qpackContext.decoderInstructionsSender(bytes -> decoderWriter.scheduleForWriting(BufferData.create(bytes), false));
    }

    private static Http3ControlStreamListener controlStreamListener(Http3QpackContext qpackContext) {
        return new Http3ControlStreamListener() {
            @Override
            public void onSettings(Http3Settings settings) {
                qpackContext.peerSettings(settings.qpackMaxTableCapacity(), settings.qpackBlockedStreams());
            }

            @Override
            public void onGoAway(Http3GoAway goAway) {
            }
        };
    }

    private static void terminateConnection(QuicConnection connection, Throwable throwable) {
        QuicCloseCommand command = Http3ProtocolException.find(throwable)
                .filter(it -> it.scope() == Http3ProtocolException.Scope.CONNECTION)
                .map(exception -> QuicCloseCommand.application(exception.errorCode().code(), exception))
                .orElseGet(() -> QuicCloseCommand.application(Http3ErrorCode.INTERNAL_ERROR.code(), throwable));
        connection.terminate(command);
    }

    private static QuicStreamWriter openAndPrimeUniStream(QuicConnection connection,
                                                          byte[] payload,
                                                          Http3StreamType streamType) {
        QuicSenderStream stream = await(connection.openNewLocalUniStream(TIMEOUT),
                                        10,
                                        TimeUnit.SECONDS,
                                        "opening the local " + streamType + " stream");
        QuicStreamWriter writer = Http3StreamSupport.connectWriter(stream, connection, streamType);
        writer.scheduleForWriting(BufferData.create(payload), false);
        return writer;
    }

    private static RequestStream openRequestStream(QuicConnection connection) {
        QuicBidiStream stream = await(connection.openNewLocalBidiStream(TIMEOUT),
                                      10,
                                      TimeUnit.SECONDS,
                                      "opening the HTTP/3 request stream");
        return new RequestStream(stream, Http3StreamSupport.connectWriter(stream, connection));
    }

    private static <T> T await(CompletableFuture<T> future,
                               long timeout,
                               TimeUnit unit,
                               String operation) {
        try {
            return future.get(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while " + operation + ".", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed while " + operation + ".", cause);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out while " + operation + ".", e);
        }
    }

    /**
     * Immutable decoded low-level HTTP/3 response details.
     */
    public static final class DecodedResponse {
        private final int status;
        private final Headers headers;
        private final Map<String, List<String>> headerValues;
        private final int headersPayloadLength;
        private final byte[] body;

        private DecodedResponse(int status, Headers headers, int headersPayloadLength, byte[] body) {
            this.status = status;
            this.headers = materializeHeaders(headers);
            Map<String, List<String>> headerValues = new LinkedHashMap<>();
            for (Header header : this.headers) {
                headerValues.put(header.headerName().lowerCase(), List.copyOf(header.allValues()));
            }
            this.headerValues = Map.copyOf(headerValues);
            this.headersPayloadLength = headersPayloadLength;
            this.body = Objects.requireNonNull(body, "body").clone();
        }

        /**
         * Create a decoded response snapshot.
         *
         * @param status HTTP status code
         * @param headers response headers
         * @param headersPayloadLength encoded HEADERS payload length in bytes
         * @param body raw DATA payload bytes
         * @return decoded response snapshot
         */
        public static DecodedResponse create(int status,
                                             Headers headers,
                                             int headersPayloadLength,
                                             byte[] body) {
            return new DecodedResponse(status, headers, headersPayloadLength, body);
        }

        /**
         * HTTP status code.
         *
         * @return HTTP status code
         */
        public int status() {
            return status;
        }

        /**
         * Defensive-copy access to the decoded response headers.
         *
         * @return copied response headers
         */
        public Headers headers() {
            return materializeHeaders(headers);
        }

        /**
         * Encoded HEADERS payload length in bytes.
         *
         * @return encoded HEADERS payload length
         */
        public int headersPayloadLength() {
            return headersPayloadLength;
        }

        /**
         * Defensive-copy access to the decoded response body.
         *
         * @return copied response body bytes
         */
        public byte[] body() {
            return body.clone();
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof DecodedResponse that)) {
                return false;
            }
            return status == that.status
                    && headersPayloadLength == that.headersPayloadLength
                    && headerValues.equals(that.headerValues)
                    && Arrays.equals(body, that.body);
        }

        @Override
        public int hashCode() {
            int result = Integer.hashCode(status);
            result = 31 * result + headerValues.hashCode();
            result = 31 * result + Integer.hashCode(headersPayloadLength);
            return 31 * result + Arrays.hashCode(body);
        }

        @Override
        public String toString() {
            return "DecodedResponse["
                    + "status=" + status
                    + ", headerCount=" + headerValues.size()
                    + ", headersPayloadLength=" + headersPayloadLength
                    + ", bodyLength=" + body.length
                    + ']';
        }

        private static Headers materializeHeaders(Headers headers) {
            WritableHeaders<?> result = WritableHeaders.create();
            for (Header header : Objects.requireNonNull(headers, "headers")) {
                List<String> values = List.copyOf(header.allValues());
                result.set(HeaderValues.create(header.headerName(),
                                               header.changing(),
                                               header.sensitive(),
                                               values.toArray(String[]::new)));
            }
            return result;
        }
    }

    private record RequestStream(QuicBidiStream stream, QuicStreamWriter writer) {
    }

    private record DecodedResponseHead(int status, Headers headers) {
        private DecodedResponseHead {
            headers = WritableHeaders.create(headers);
        }
    }
}
