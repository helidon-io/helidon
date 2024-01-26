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

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.http2.Http2Settings;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.DefaultDnsResolver;
import io.helidon.webclient.api.DnsAddressLookup;
import io.helidon.webclient.api.TcpClientConnection;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http2.Http2ClientConnection;
import io.helidon.webclient.http2.Http2ClientImpl;
import io.helidon.webclient.http2.Http2StreamConfig;

/**
 * A gRPC client call handler. The typical order of calls will be:
 *
 *      start request* sendMessage* halfClose
 *
 * @param <ReqT>
 * @param <ResT>
 */
class GrpcClientCall<ReqT, ResT> extends ClientCall<ReqT, ResT> {
    private static final Header GRPC_CONTENT_TYPE = HeaderValues.create(HeaderNames.CONTENT_TYPE, "application/grpc");

    private final AtomicReference<Listener<ResT>> responseListener = new AtomicReference<>();
    private final GrpcClientImpl grpcClient;
    private final GrpcClientMethodDescriptor method;
    private final AtomicInteger messages = new AtomicInteger();

    GrpcClientCall(GrpcClientImpl grpcClient, GrpcClientMethodDescriptor method) {
        this.grpcClient = grpcClient;
        this.method = method;
    }

    @Override
    public void start(Listener<ResT> responseListener, Metadata headers) {
        if (this.responseListener.compareAndSet(null, responseListener)) {
            // obtain HTTP2 connection
            Http2ClientConnection connection = Http2ClientConnection.create(
                    (Http2ClientImpl) grpcClient.http2Client(), clientConnection(), true);

            // create HTTP2 stream from connection
            GrpcClientStream clientStream = new GrpcClientStream(
                    connection,
                    Http2Settings.create(),     // Http2Settings
                    null,                       // SocketContext
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
                            return grpcClient.prototype().readTimeout().orElse(null);
                        }
                    },
                    null,       // Http2ClientConfig
                    connection.streamIdSequence());

            // send HEADERS frame
        } else {
            throw new IllegalStateException("Response listener was already set");
        }
    }

    @Override
    public void request(int numMessages) {
        messages.addAndGet(numMessages);
    }

    @Override
    public void cancel(String message, Throwable cause) {
        // close the stream/connection via RST_STREAM
        // can be closed even if halfClosed
    }

    @Override
    public void halfClose() {
        // close the stream/connection
        // GOAWAY frame
    }

    @Override
    public void sendMessage(ReqT message) {
        // send a DATA frame
    }

    private ClientConnection clientConnection() {
        GrpcClientConfig clientConfig = grpcClient.prototype();
        ClientUri clientUri = clientConfig.baseUri().orElseThrow();
        WebClient webClient = grpcClient.webClient();

        ConnectionKey connectionKey = new ConnectionKey(
                clientUri.scheme(),
                clientUri.host(),
                clientUri.port(),
                clientConfig.readTimeout().orElse(null),
                null,
                DefaultDnsResolver.create(),
                DnsAddressLookup.defaultLookup(),
                null);

        return TcpClientConnection.create(webClient,
                connectionKey,
                Collections.emptyList(),
                connection -> false,
                connection -> {}).connect();
    }
}
