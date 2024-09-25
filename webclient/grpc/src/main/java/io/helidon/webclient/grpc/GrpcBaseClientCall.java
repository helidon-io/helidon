/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.webclient.grpc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.Executor;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.DefaultDnsResolver;
import io.helidon.webclient.api.DnsAddressLookup;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.TcpClientConnection;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http2.Http2ClientConnection;
import io.helidon.webclient.http2.Http2ClientImpl;
import io.helidon.webclient.http2.Http2StreamConfig;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

import static java.lang.System.Logger.Level.DEBUG;

/**
 * Base class for gRPC client calls.
 */
abstract class GrpcBaseClientCall<ReqT, ResT> extends ClientCall<ReqT, ResT> {
    private static final System.Logger LOGGER = System.getLogger(GrpcBaseClientCall.class.getName());

    protected static final Metadata EMPTY_METADATA = new Metadata();
    protected static final Header GRPC_ACCEPT_ENCODING = HeaderValues.create(HeaderNames.ACCEPT_ENCODING, "gzip");
    protected static final Header GRPC_CONTENT_TYPE = HeaderValues.create(HeaderNames.CONTENT_TYPE, "application/grpc");

    protected static final BufferData PING_FRAME = BufferData.create("PING");
    protected static final BufferData EMPTY_BUFFER_DATA = BufferData.empty();

    private final GrpcClientImpl grpcClient;
    private final GrpcChannel grpcChannel;
    private final MethodDescriptor<ReqT, ResT> methodDescriptor;
    private final CallOptions callOptions;
    private final int initBufferSize;
    private final Duration pollWaitTime;
    private final boolean abortPollTimeExpired;
    private final Duration heartbeatPeriod;
    private final ClientUriSupplier clientUriSupplier;

    private final MethodDescriptor.Marshaller<ReqT> requestMarshaller;
    private final MethodDescriptor.Marshaller<ResT> responseMarshaller;

    private volatile Http2ClientConnection connection;
    private volatile GrpcClientStream clientStream;
    private volatile Listener<ResT> responseListener;
    private volatile HelidonSocket socket;

    GrpcBaseClientCall(GrpcChannel grpcChannel, MethodDescriptor<ReqT, ResT> methodDescriptor, CallOptions callOptions) {
        this.grpcClient = (GrpcClientImpl) grpcChannel.grpcClient();
        this.grpcChannel = grpcChannel;
        this.methodDescriptor = methodDescriptor;
        this.callOptions = callOptions;
        this.requestMarshaller = methodDescriptor.getRequestMarshaller();
        this.responseMarshaller = methodDescriptor.getResponseMarshaller();
        this.initBufferSize = grpcClient.prototype().protocolConfig().initBufferSize();
        this.pollWaitTime = grpcClient.prototype().protocolConfig().pollWaitTime();
        this.abortPollTimeExpired = grpcClient.prototype().protocolConfig().abortPollTimeExpired();
        this.heartbeatPeriod = grpcClient.prototype().protocolConfig().heartbeatPeriod();
        this.clientUriSupplier = grpcClient.prototype().clientUriSupplier().orElse(null);
    }

    @Override
    public void start(Listener<ResT> responseListener, Metadata metadata) {
        LOGGER.log(DEBUG, "start called");

        this.responseListener = responseListener;

        // obtain HTTP2 connection
        ClientUri clientUri = nextClientUri();
        ClientConnection clientConnection = clientConnection(clientUri);
        socket = clientConnection.helidonSocket();
        connection = Http2ClientConnection.create((Http2ClientImpl) grpcClient.http2Client(),
                                                  clientConnection, true);

        // create HTTP2 stream from connection
        clientStream = new GrpcClientStream(
                connection,
                Http2Settings.create(),                 // Http2Settings
                socket,                                 // SocketContext
                new Http2StreamConfig() {
                    @Override
                    public boolean priorKnowledge() {
                        return true;
                    }

                    @Override
                    public int priority() {
                        return 0;
                    }

                    @Override
                    public Duration readTimeout() {
                        return grpcClient.prototype().readTimeout().orElse(
                                grpcClient.prototype().protocolConfig().pollWaitTime());
                    }
                },
                null,       // Http2ClientConfig
                connection.streamIdSequence());

        // start streaming threads
        startStreamingThreads();

        // send HEADERS frame
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(Http2Headers.AUTHORITY_NAME, clientUri.authority());
        headers.add(Http2Headers.METHOD_NAME, "POST");
        headers.add(Http2Headers.PATH_NAME, "/" + methodDescriptor.getFullMethodName());
        headers.add(Http2Headers.SCHEME_NAME, "http");
        headers.add(GRPC_CONTENT_TYPE);
        headers.add(GRPC_ACCEPT_ENCODING);
        clientStream.writeHeaders(Http2Headers.create(headers), false);
    }

    abstract void startStreamingThreads();

    /**
     * Unary blocking calls that use stubs provide their own executor which needs
     * to be used at least once to unblock the calling thread and complete the
     * gRPC invocation. This method submits an empty task for that purpose. There
     * may be a better way to achieve this.
     */
    protected void unblockUnaryExecutor() {
        Executor executor = callOptions.getExecutor();
        if (executor != null) {
            try {
                executor.execute(() -> {
                });
            } catch (Throwable t) {
                // ignored
            }
        }
    }

    protected GrpcClientImpl grpcClient() {
        return grpcClient;
    }

    protected ClientConnection clientConnection(ClientUri clientUri) {
        WebClient webClient = grpcClient.webClient();
        GrpcClientConfig clientConfig = grpcClient.prototype();
        ConnectionKey connectionKey = new ConnectionKey(
                clientUri.scheme(),
                clientUri.host(),
                clientUri.port(),
                clientConfig.readTimeout().orElse(Duration.ZERO),
                clientConfig.tls(),
                DefaultDnsResolver.create(),
                DnsAddressLookup.defaultLookup(),
                Proxy.noProxy());
        return TcpClientConnection.create(webClient,
                                          connectionKey,
                                          Collections.emptyList(),
                                          connection -> false,
                                          connection -> {
                                          }).connect();
    }

    protected boolean isRemoteOpen() {
        return clientStream.streamState() != Http2StreamState.HALF_CLOSED_REMOTE
                && clientStream.streamState() != Http2StreamState.CLOSED;
    }

    protected ResT toResponse(BufferData bufferData) {
        bufferData.read();                  // compression
        bufferData.readUnsignedInt32();     // length prefixed
        return responseMarshaller.parse(new InputStream() {
            @Override
            public int read() {
                return bufferData.available() > 0 ? bufferData.read() : -1;
            }
        });
    }

    protected byte[] serializeMessage(ReqT message) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(initBufferSize);
        try (InputStream is = requestMarshaller().stream(message)) {
            is.transferTo(baos);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return baos.toByteArray();
    }

    protected Duration heartbeatPeriod() {
        return heartbeatPeriod;
    }

    protected boolean abortPollTimeExpired() {
        return abortPollTimeExpired;
    }

    protected Duration pollWaitTime() {
        return pollWaitTime;
    }

    protected Http2ClientConnection connection() {
        return connection;
    }

    protected MethodDescriptor.Marshaller<ReqT> requestMarshaller() {
        return requestMarshaller;
    }

    protected GrpcClientStream clientStream() {
        return clientStream;
    }

    protected Listener<ResT> responseListener() {
        return responseListener;
    }

    protected HelidonSocket socket() {
        return socket;
    }

    /**
     * Retrieves the next URI either from the supplier or directly from config. If
     * a supplier is provided, it will take precedence.
     *
     * @return the next {@link ClientUri}
     * @throws java.util.NoSuchElementException if supplier has been exhausted
     */
    private ClientUri nextClientUri() {
        return clientUriSupplier == null ? grpcClient.prototype().baseUri().orElseThrow()
                : clientUriSupplier.next();
    }
}
