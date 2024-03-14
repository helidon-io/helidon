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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.webclient.http2.StreamTimeoutException;

import io.grpc.CallOptions;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;

/**
 * An implementation of a gRPC call. Expects:
 * <p>
 * start (request | sendMessage)* (halfClose | cancel)
 *
 * @param <ReqT> request type
 * @param <ResT> response type
 */
class GrpcClientCall<ReqT, ResT> extends GrpcBaseClientCall<ReqT, ResT> {
    private static final System.Logger LOGGER = System.getLogger(GrpcClientCall.class.getName());

    private final ExecutorService executor;
    private final AtomicInteger messageRequest = new AtomicInteger();

    private final LinkedBlockingQueue<BufferData> sendingQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<BufferData> receivingQueue = new LinkedBlockingQueue<>();

    private final CountDownLatch startReadBarrier = new CountDownLatch(1);
    private final CountDownLatch startWriteBarrier = new CountDownLatch(1);

    private volatile Future<?> readStreamFuture;
    private volatile Future<?> writeStreamFuture;

    GrpcClientCall(GrpcClientImpl grpcClient, MethodDescriptor<ReqT, ResT> methodDescriptor, CallOptions callOptions) {
        super(grpcClient, methodDescriptor, callOptions);
        this.executor = grpcClient.webClient().executor();
    }

    @Override
    public void request(int numMessages) {
        socket().log(LOGGER, DEBUG, "request called %d", numMessages);
        messageRequest.addAndGet(numMessages);
        startReadBarrier.countDown();
    }

    @Override
    public void cancel(String message, Throwable cause) {
        socket().log(LOGGER, DEBUG, "cancel called %s", message);
        responseListener().onClose(Status.CANCELLED, EMPTY_METADATA);
        readStreamFuture.cancel(true);
        writeStreamFuture.cancel(true);
        close();
    }

    @Override
    public void halfClose() {
        socket().log(LOGGER, DEBUG, "halfClose called");
        sendingQueue.add(EMPTY_BUFFER_DATA);       // end marker
    }

    @Override
    public void sendMessage(ReqT message) {
        socket().log(LOGGER, DEBUG, "sendMessage called");
        BufferData messageData = BufferData.growing(BUFFER_SIZE_BYTES);
        messageData.readFrom(requestMarshaller().stream(message));
        BufferData headerData = BufferData.create(5);
        headerData.writeInt8(0);                                // no compression
        headerData.writeUnsignedInt32(messageData.available());         // length prefixed
        sendingQueue.add(BufferData.create(headerData, messageData));
        startWriteBarrier.countDown();
    }

    protected void startStreamingThreads() {
        // write streaming thread
        writeStreamFuture = executor.submit(() -> {
            try {
                startWriteBarrier.await();
                socket().log(LOGGER, DEBUG, "[Writing thread] started");

                boolean endOfStream = false;
                while (isRemoteOpen()) {
                    socket().log(LOGGER, DEBUG, "[Writing thread] polling sending queue");
                    BufferData bufferData = sendingQueue.poll(WAIT_TIME_MILLIS, TimeUnit.MILLISECONDS);
                    if (bufferData != null) {
                        if (bufferData == EMPTY_BUFFER_DATA) {     // end marker
                            socket().log(LOGGER, DEBUG, "[Writing thread] sending queue end marker found");
                            if (!endOfStream) {
                                socket().log(LOGGER, DEBUG, "[Writing thread] sending empty buffer to end stream");
                                clientStream().writeData(EMPTY_BUFFER_DATA, true);
                            }
                            break;
                        }
                        endOfStream = (sendingQueue.peek() == EMPTY_BUFFER_DATA);
                        boolean lastEndOfStream = endOfStream;
                        socket().log(LOGGER, DEBUG, "[Writing thread] writing bufferData %b", lastEndOfStream);
                        clientStream().writeData(bufferData, endOfStream);
                    }
                }
            } catch (Throwable e) {
                socket().log(LOGGER, ERROR, e.getMessage(), e);
            }
            socket().log(LOGGER, DEBUG, "[Writing thread] exiting");
        });

        // read streaming thread
        readStreamFuture = executor.submit(() -> {
            try {
                startReadBarrier.await();
                socket().log(LOGGER, DEBUG, "[Reading thread] started");

                // read response headers
                clientStream().readHeaders();

                while (isRemoteOpen()) {
                    // drain queue
                    drainReceivingQueue();

                    // trailers received?
                    if (clientStream().trailers().isDone()) {
                        socket().log(LOGGER, DEBUG, "[Reading thread] trailers received");
                        break;
                    }

                    // attempt to read and queue
                    Http2FrameData frameData;
                    try {
                        frameData = clientStream().readOne(WAIT_TIME_MILLIS_DURATION);
                    } catch (StreamTimeoutException e) {
                        socket().log(LOGGER, ERROR, "[Reading thread] read timeout");
                        continue;
                    }
                    if (frameData != null) {
                        receivingQueue.add(frameData.data());
                        socket().log(LOGGER, DEBUG, "[Reading thread] adding bufferData to receiving queue");
                    }
                }

                socket().log(LOGGER, DEBUG, "[Reading thread] closing listener");
                responseListener().onClose(Status.OK, EMPTY_METADATA);
            } catch (Throwable e) {
                socket().log(LOGGER, ERROR, e.getMessage(), e);
                responseListener().onClose(Status.UNKNOWN, EMPTY_METADATA);
            } finally {
                close();
            }
            socket().log(LOGGER, DEBUG, "[Reading thread] exiting");
        });
    }

    private void close() {
        socket().log(LOGGER, DEBUG, "closing client call");
        sendingQueue.clear();
        clientStream().cancel();
        connection().close();
        unblockUnaryExecutor();
    }

    private void drainReceivingQueue() {
        socket().log(LOGGER, DEBUG, "[Reading thread] draining receiving queue");
        while (messageRequest.get() > 0 && !receivingQueue.isEmpty()) {
            messageRequest.getAndDecrement();
            ResT res = toResponse(receivingQueue.remove());
            socket().log(LOGGER, DEBUG, "[Reading thread] sending response to listener");
            responseListener().onMessage(res);
        }
    }
}
