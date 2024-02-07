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

import java.io.InputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.tls.Tls;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2Settings;
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

/**
 * A gRPC client call handler. The typical order of calls will be:
 *
 * start request* sendMessage* halfClose
 *
 * TODO: memory synchronization across method calls
 *
 * @param <ReqT>
 * @param <ResT>
 */
class GrpcClientCall<ReqT, ResT> extends ClientCall<ReqT, ResT> {
    private static final Header GRPC_ACCEPT_ENCODING = HeaderValues.create(HeaderNames.ACCEPT_ENCODING, "gzip");
    private static final Header GRPC_CONTENT_TYPE = HeaderValues.create(HeaderNames.CONTENT_TYPE, "application/grpc");

    private final GrpcClientImpl grpcClient;
    private final GrpcClientMethodDescriptor method;
    private final AtomicInteger messages = new AtomicInteger();

    private final MethodDescriptor.Marshaller<ReqT> requestMarshaller;
    private final MethodDescriptor.Marshaller<ResT> responseMarshaller;
    private final Queue<BufferData> messageQueue = new LinkedBlockingQueue<>();

    private volatile Http2ClientConnection connection;
    private volatile GrpcClientStream clientStream;
    private volatile Listener<ResT> responseListener;

    @SuppressWarnings("unchecked")
    GrpcClientCall(GrpcClientImpl grpcClient, GrpcClientMethodDescriptor method) {
        this.grpcClient = grpcClient;
        this.method = method;
        this.requestMarshaller = (MethodDescriptor.Marshaller<ReqT>) method.descriptor().getRequestMarshaller();
        this.responseMarshaller = (MethodDescriptor.Marshaller<ResT>) method.descriptor().getResponseMarshaller();
    }

    @Override
    public void start(Listener<ResT> responseListener, Metadata metadata) {
        this.responseListener = responseListener;

        // obtain HTTP2 connection
        connection = Http2ClientConnection.create((Http2ClientImpl) grpcClient.http2Client(),
                clientConnection(), true);

        // create HTTP2 stream from connection
        clientStream = new GrpcClientStream(
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
                        return grpcClient.prototype().readTimeout().orElse(Duration.ofSeconds(60));
                    }
                },
                null,       // Http2ClientConfig
                connection.streamIdSequence());

        // send HEADERS frame
        ClientUri clientUri = grpcClient.prototype().baseUri().orElseThrow();
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(Http2Headers.AUTHORITY_NAME, clientUri.authority());
        headers.add(Http2Headers.METHOD_NAME, "POST");
        headers.add(Http2Headers.PATH_NAME, "/" + method.descriptor().getFullMethodName());
        headers.add(Http2Headers.SCHEME_NAME, "http");
        headers.add(GRPC_CONTENT_TYPE);
        headers.add(GRPC_ACCEPT_ENCODING);
        clientStream.writeHeaders(Http2Headers.create(headers), false);
    }

    @Override
    public void request(int numMessages) {
        messages.addAndGet(numMessages);

        ExecutorService executor = grpcClient.webClient().executor();
        executor.submit(() -> {
            clientStream.readHeaders();
            while (messages.decrementAndGet() > 0) {
                BufferData bufferData = clientStream.read();
                bufferData.read();                  // compression
                bufferData.readUnsignedInt32();     // length prefixed
                ResT res = responseMarshaller.parse(new InputStream() {
                    @Override
                    public int read() {
                        return bufferData.available() > 0 ? bufferData.read() : -1;
                    }
                });
                responseListener.onMessage(res);
            }
            responseListener.onClose(Status.OK, new Metadata());
            clientStream.close();
            connection.close();
        });
    }

    @Override
    public void cancel(String message, Throwable cause) {
        // close the stream/connection via RST_STREAM
        messageQueue.clear();
        clientStream.cancel();
        connection.close();
    }

    @Override
    public void halfClose() {
        // drain the message queue
        while (!messageQueue.isEmpty()) {
            BufferData msg = messageQueue.poll();
            clientStream.writeData(msg, messageQueue.isEmpty());
        }
    }

    @Override
    public void sendMessage(ReqT message) {
        // queue a message
        BufferData messageData = BufferData.growing(512);
        messageData.readFrom(requestMarshaller.stream(message));
        BufferData headerData = BufferData.create(5);
        headerData.writeInt8(0);                                // no compression
        headerData.writeUnsignedInt32(messageData.available());         // length prefixed
        messageQueue.add(BufferData.create(headerData, messageData));
    }

    private ClientConnection clientConnection() {
        GrpcClientConfig clientConfig = grpcClient.prototype();
        ClientUri clientUri = clientConfig.baseUri().orElseThrow();
        WebClient webClient = grpcClient.webClient();

        Tls tls = Tls.builder().enabled(false).build();
        ConnectionKey connectionKey = new ConnectionKey(
                clientUri.scheme(),
                clientUri.host(),
                clientUri.port(),
                clientConfig.readTimeout().orElse(Duration.ZERO),
                tls,
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
}
