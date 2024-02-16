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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

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
import io.helidon.http.http2.Http2FrameData;
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
import io.helidon.webclient.http2.StreamTimeoutException;

/**
 * A gRPC client call handler. The typical order of calls will be:
 *
 * start (request | sendMessage)* (halfClose | cancel)
 *
 * @param <ReqT>
 * @param <ResT>
 */
class GrpcClientCall<ReqT, ResT> extends ClientCall<ReqT, ResT> {
    private static final Logger LOGGER = Logger.getLogger(GrpcClientCall.class.getName());

    private static final Header GRPC_ACCEPT_ENCODING = HeaderValues.create(HeaderNames.ACCEPT_ENCODING, "gzip");
    private static final Header GRPC_CONTENT_TYPE = HeaderValues.create(HeaderNames.CONTENT_TYPE, "application/grpc");

    private static final int WAIT_TIME_MILLIS = 100;
    private static final Duration WAIT_TIME_MILLIS_DURATION = Duration.ofMillis(WAIT_TIME_MILLIS);

    private static final BufferData EMPTY_BUFFER_DATA = BufferData.empty();

    private final ExecutorService executor;
    private final GrpcClientImpl grpcClient;
    private final MethodDescriptor<ReqT, ResT> methodDescriptor;
    private final AtomicInteger messageRequest = new AtomicInteger();

    private final MethodDescriptor.Marshaller<ReqT> requestMarshaller;
    private final MethodDescriptor.Marshaller<ResT> responseMarshaller;

    private final LinkedBlockingQueue<BufferData> sendingQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<BufferData> receivingQueue = new LinkedBlockingQueue<>();

    private final CountDownLatch startReadBarrier = new CountDownLatch(1);
    private final CountDownLatch startWriteBarrier = new CountDownLatch(1);

    private volatile Http2ClientConnection connection;
    private volatile GrpcClientStream clientStream;
    private volatile Listener<ResT> responseListener;
    private volatile Future<?> readStreamFuture;
    private volatile Future<?> writeStreamFuture;

    GrpcClientCall(GrpcClientImpl grpcClient, MethodDescriptor<ReqT, ResT> methodDescriptor) {
        this.grpcClient = grpcClient;
        this.methodDescriptor = methodDescriptor;
        this.requestMarshaller = methodDescriptor.getRequestMarshaller();
        this.responseMarshaller = methodDescriptor.getResponseMarshaller();
        this.executor = grpcClient.webClient().executor();
    }

    @Override
    public void start(Listener<ResT> responseListener, Metadata metadata) {
        LOGGER.finest("start called");

        this.responseListener = responseListener;

        // obtain HTTP2 connection
        ClientConnection clientConnection = clientConnection();
        connection = Http2ClientConnection.create((Http2ClientImpl) grpcClient.http2Client(),
                clientConnection, true);

        // create HTTP2 stream from connection
        clientStream = new GrpcClientStream(
                connection,
                Http2Settings.create(),                 // Http2Settings
                clientConnection.helidonSocket(),       // SocketContext
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
                        return grpcClient.prototype().readTimeout().orElse(Duration.ofSeconds(10));
                    }
                },
                null,       // Http2ClientConfig
                connection.streamIdSequence());

        // start streaming threads
        startStreamingThreads();

        // send HEADERS frame
        ClientUri clientUri = grpcClient.prototype().baseUri().orElseThrow();
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(Http2Headers.AUTHORITY_NAME, clientUri.authority());
        headers.add(Http2Headers.METHOD_NAME, "POST");
        headers.add(Http2Headers.PATH_NAME, "/" + methodDescriptor.getFullMethodName());
        headers.add(Http2Headers.SCHEME_NAME, "http");
        headers.add(GRPC_CONTENT_TYPE);
        headers.add(GRPC_ACCEPT_ENCODING);
        clientStream.writeHeaders(Http2Headers.create(headers), false);
    }

    @Override
    public void request(int numMessages) {
        LOGGER.finest(() -> "request called " + numMessages);
        messageRequest.addAndGet(numMessages);
        startReadBarrier.countDown();
    }

    @Override
    public void cancel(String message, Throwable cause) {
        LOGGER.finest(() -> "cancel called " + message);
        responseListener.onClose(Status.CANCELLED, new Metadata());
        close();
    }

    @Override
    public void halfClose() {
        LOGGER.finest("halfClose called");
        sendingQueue.add(EMPTY_BUFFER_DATA);       // end marker
    }

    @Override
    public void sendMessage(ReqT message) {
        LOGGER.finest("sendMessage called");
        BufferData messageData = BufferData.growing(512);
        messageData.readFrom(requestMarshaller.stream(message));
        BufferData headerData = BufferData.create(5);
        headerData.writeInt8(0);                                // no compression
        headerData.writeUnsignedInt32(messageData.available());         // length prefixed
        sendingQueue.add(BufferData.create(headerData, messageData));
        startWriteBarrier.countDown();
    }

    private void startStreamingThreads() {
        // write streaming thread
        writeStreamFuture = executor.submit(() -> {
            try {
                startWriteBarrier.await();
                LOGGER.fine("[Writing thread] started");

                boolean endOfStream = false;
                while (isRemoteOpen()) {
                    LOGGER.finest("[Writing thread] polling sending queue");
                    BufferData bufferData = sendingQueue.poll(WAIT_TIME_MILLIS, TimeUnit.MILLISECONDS);
                    if (bufferData != null) {
                        if (bufferData == EMPTY_BUFFER_DATA) {     // end marker
                            LOGGER.finest("[Writing thread] sending queue end marker found");
                            if (!endOfStream) {
                                LOGGER.finest("[Writing thread] sending empty buffer to end stream");
                                clientStream.writeData(EMPTY_BUFFER_DATA, true);
                            }
                            break;
                        }
                        endOfStream = (sendingQueue.peek() == EMPTY_BUFFER_DATA);
                        boolean lastEndOfStream = endOfStream;
                        LOGGER.finest(() -> "[Writing thread] writing bufferData " + lastEndOfStream);
                        clientStream.writeData(bufferData, endOfStream);
                    }
                }
            } catch (Throwable e) {
                LOGGER.finest(e.getMessage());
            }
            LOGGER.fine("[Writing thread] exiting");
        });

        // read streaming thread
        readStreamFuture = executor.submit(() -> {
            try {
                startReadBarrier.await();
                LOGGER.fine("[Reading thread] started");

                // read response headers
                clientStream.readHeaders();

                while (isRemoteOpen()) {
                    // drain queue
                    drainReceivingQueue();

                    // trailers received?
                    if (clientStream.trailers().isDone()) {
                        LOGGER.finest("[Reading thread] trailers received");
                        break;
                    }

                    // attempt to read and queue
                    Http2FrameData frameData;
                    try {
                        frameData = clientStream.readOne(WAIT_TIME_MILLIS_DURATION);
                    } catch (StreamTimeoutException e) {
                        LOGGER.fine("[Reading thread] read timeout");
                        continue;
                    }
                    if (frameData != null) {
                        receivingQueue.add(frameData.data());
                        LOGGER.finest("[Reading thread] adding bufferData to receiving queue");
                    }
                }

                LOGGER.finest("[Reading thread] closing listener");
                responseListener.onClose(Status.OK, new Metadata());
            } catch (Throwable e) {
                LOGGER.finest(e.getMessage());
                responseListener.onClose(Status.UNKNOWN, new Metadata());
            } finally {
                close();
            }
            LOGGER.fine("[Reading thread] exiting");
        });
    }

    private void close() {
        LOGGER.finest("closing client call");
        readStreamFuture.cancel(true);
        writeStreamFuture.cancel(true);
        sendingQueue.clear();
        clientStream.cancel();
        connection.close();
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

    private boolean isRemoteOpen() {
        return clientStream.streamState() != Http2StreamState.HALF_CLOSED_REMOTE
                && clientStream.streamState() != Http2StreamState.CLOSED;
    }

    private ResT toResponse(BufferData bufferData) {
        bufferData.read();                  // compression
        bufferData.readUnsignedInt32();     // length prefixed
        return responseMarshaller.parse(new InputStream() {
            @Override
            public int read() {
                return bufferData.available() > 0 ? bufferData.read() : -1;
            }
        });
    }

    private void drainReceivingQueue() {
        LOGGER.finest("[Reading thread] draining receiving queue");
        while (messageRequest.get() > 0 && !receivingQueue.isEmpty()) {
            messageRequest.getAndDecrement();
            ResT res = toResponse(receivingQueue.remove());
            LOGGER.finest("[Reading thread] sending response to listener");
            responseListener.onMessage(res);
        }
    }
}
