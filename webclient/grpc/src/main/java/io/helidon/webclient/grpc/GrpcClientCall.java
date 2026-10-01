/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.Header;
import io.helidon.http.Headers;
import io.helidon.http.http2.Http2Headers;
import io.helidon.webclient.http2.StreamTimeoutException;

import io.grpc.CallOptions;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;

/**
 * An implementation of a gRPC call.
 *
 * @param <ReqT> request type
 * @param <ResT> response type
 */
class GrpcClientCall<ReqT, ResT> extends GrpcBaseClientCall<ReqT, ResT> {
    private static final System.Logger LOGGER = System.getLogger(GrpcClientCall.class.getName());

    private final ExecutorService executor;
    private final Semaphore messageRequest = new Semaphore(0);

    private final LinkedBlockingQueue<BufferData> sendingQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<BufferData> receivingQueue = new LinkedBlockingQueue<>(1);

    private final CountDownLatch startReadBarrier = new CountDownLatch(1);
    private final CountDownLatch startWriteBarrier = new CountDownLatch(1);

    private volatile Future<?> readStreamFuture;
    private volatile Future<?> writeStreamFuture;
    private volatile Future<?> heartbeatFuture;
    private volatile Thread readStreamThread;
    private volatile Thread writeStreamThread;

    GrpcClientCall(GrpcChannel grpcChannel, MethodDescriptor<ReqT, ResT> methodDescriptor, CallOptions callOptions) {
        super(grpcChannel, methodDescriptor, callOptions);
        this.executor = grpcClient().webClient().executor();
    }

    @Override
    public void request(int numMessages) {
        if (isClosed()) {
            return;
        }
        LOGGER.log(DEBUG, "request called {0}", numMessages);
        if (numMessages < 1) {
            close(Status.INVALID_ARGUMENT);
            return;
        }
        messageRequest.release(numMessages);
        startReadBarrier.countDown();
    }

    @Override
    public void halfClose() {
        if (isClosed()) {
            return;
        }
        socket().log(LOGGER, DEBUG, "halfClose called");
        sendingQueue.add(EMPTY_BUFFER_DATA);       // end marker
        startWriteBarrier.countDown();
    }

    @Override
    public void sendMessage(ReqT message) {
        if (isClosed()) {
            return;
        }
        // serialize and queue message for writing
        byte[] serialized = serializeMessage(message);
        BufferData messageData = BufferData.createReadOnly(serialized, 0, serialized.length);
        BufferData headerData = BufferData.create(DATA_PREFIX_LENGTH);
        headerData.writeInt8(0);                                // no compression
        headerData.writeUnsignedInt32(messageData.available());         // length prefixed
        sendingQueue.add(BufferData.create(headerData, messageData));
        startWriteBarrier.countDown();
    }

    protected void startStreamingThreads() {
        // heartbeat thread
        Duration period = heartbeatPeriod();
        if (!period.isZero()) {
            heartbeatFuture = executor.submit(() -> {
                try {
                    startWriteBarrier.await();
                    socket().log(LOGGER, DEBUG, "[Heartbeat thread] started with period " + period);
                    while (!isClosed() && isRemoteOpen()) {
                        Thread.sleep(period);
                        if (sendingQueue.isEmpty()) {
                            sendingQueue.add(PING_FRAME);
                        }
                    }
                } catch (Throwable t) {
                    socket().log(LOGGER, DEBUG, "[Heartbeat thread] exception " + t.getMessage());
                }
            });
        } else {
            heartbeatFuture = CompletableFuture.completedFuture(null);
        }

        // write streaming thread
        writeStreamFuture = executor.submit(() -> {
            writeStreamThread = Thread.currentThread();
            try {
                startWriteBarrier.await();
                socket().log(LOGGER, DEBUG, "[Writing thread] started");

                boolean endOfStream = false;
                while (!isClosed() && isRemoteOpen()) {
                    socket().log(LOGGER, DEBUG, "[Writing thread] polling sending queue");
                    BufferData bufferData = sendingQueue.poll(pollWaitTime().toMillis(), TimeUnit.MILLISECONDS);
                    if (bufferData != null) {
                        if (bufferData == PING_FRAME) {                   // ping frame
                            clientStream().sendPing();
                            continue;
                        }
                        if (bufferData == EMPTY_BUFFER_DATA) {            // end marker
                            if (!endOfStream) {
                                clientStream().writeData(EMPTY_BUFFER_DATA, true);
                            }
                            break;
                        }
                        endOfStream = (sendingQueue.peek() == EMPTY_BUFFER_DATA);
                        boolean lastEndOfStream = endOfStream;
                        socket().log(LOGGER, DEBUG, "[Writing thread] writing bufferData %b", lastEndOfStream);
                        // update bytes sent
                        if (enableMetrics()) {
                            bytesSent().addAndGet(bufferData.available());
                        }
                        clientStream().writeData(bufferData, endOfStream);
                    }
                }
            } catch (Throwable e) {
                if (!isClosed()) {
                    socket().log(LOGGER, ERROR, e.getMessage(), e);
                    close(Status.UNKNOWN.withDescription(e.getMessage()).withCause(e));
                }
            } finally {
                writeStreamThread = null;
            }
            socket().log(LOGGER, DEBUG, "[Writing thread] exiting");
        });

        // read streaming thread
        readStreamFuture = executor.submit(() -> {
            readStreamThread = Thread.currentThread();
            try {
                startReadBarrier.await();
                socket().log(LOGGER, DEBUG, "[Reading thread] started");

                // read response headers
                Status status = Status.OK;
                boolean headersRead = false;
                do {
                    try {
                        Http2Headers headers = clientStream().readHeaders();
                        if (headers.httpHeaders().contains(STATUS_NAME)) {
                            Header grpcStatus = headers.httpHeaders().get(STATUS_NAME);
                            status = Status.fromCodeValue(grpcStatus.getInt());
                        }
                        headersRead = true;
                    } catch (StreamTimeoutException e) {
                        handleStreamTimeout(e);
                    }
                } while (!isClosed() && !headersRead);

                // read data from stream
                Duration nextRequestWaitTime = grpcClient().prototype().protocolConfig().nextRequestWaitTime();
                boolean requestTimedOut = false;
                while (!isClosed() && (isRemoteOpen() || hasUnreadData())) {
                    if (!drainReceivingQueue(nextRequestWaitTime)) {
                        socket().log(LOGGER, DEBUG, "[Reading thread] unable to drain receiving queue");
                        status = Status.CANCELLED;
                        requestTimedOut = true;
                        break;
                    }

                    // trailers or eos received?
                    if (clientStream().trailers().isDone() || !clientStream().hasEntity()) {
                        socket().log(LOGGER, DEBUG, "[Reading thread] trailers or eos received");
                        break;
                    }

                    // read complete gRPC data
                    BufferData bufferData = readGrpcFrame();
                    if (bufferData == null) {
                        continue;
                    }

                    // update bytes received excluding prefix
                    if (enableMetrics()) {
                        bytesRcvd().addAndGet(bufferData.available() - DATA_PREFIX_LENGTH);
                    }
                    receivingQueue.add(bufferData);
                    socket().log(LOGGER, DEBUG, "[Reading thread] adding bufferData to receiving queue");
                }

                // attempt to drain our receiving queue if permits arrive on time
                if (!requestTimedOut && !drainReceivingQueue(nextRequestWaitTime)) {
                    socket().log(LOGGER, DEBUG, "[Reading thread] unable to drain receiving queue");
                    status = Status.CANCELLED;
                }

                // report onClose call with final status
                if (clientStream().trailers().isDone()) {
                    Headers trailers = clientStream().trailers().get();
                    if (trailers.contains(STATUS_NAME)) {
                        status = Status.fromCodeValue(trailers.get(STATUS_NAME).getInt());
                    }
                }
                closeResponse(status);
            } catch (StreamTimeoutException e) {
                close(Status.DEADLINE_EXCEEDED);
            } catch (StatusRuntimeException e) {
                Metadata trailers = e.getTrailers();
                close(e.getStatus(), trailers == null ? EMPTY_METADATA : trailers);
            } catch (Throwable e) {
                if (!isClosed()) {
                    socket().log(LOGGER, ERROR, e.getMessage(), e);
                    close(Status.UNKNOWN.withDescription(e.getMessage()).withCause(e));
                }
            } finally {
                readStreamThread = null;
            }
            socket().log(LOGGER, DEBUG, "[Reading thread] exiting");
        });
    }

    @Override
    protected void closeStreamingThreads() {
        // The worker performing cleanup must remain able to write the final HTTP/2 frames.
        if (readStreamThread != Thread.currentThread()) {
            cancelFuture(readStreamFuture);
        }
        if (writeStreamThread != Thread.currentThread()) {
            cancelFuture(writeStreamFuture);
        }
        cancelFuture(heartbeatFuture);
        sendingQueue.clear();
        receivingQueue.clear();
    }

    private boolean drainReceivingQueue(Duration waitTime) throws InterruptedException {
        socket().log(LOGGER, DEBUG, "[Reading thread] draining receiving queue");
        while (!isClosed() && !receivingQueue.isEmpty()) {
            if (!messageRequest.tryAcquire(waitTime.toNanos(), TimeUnit.NANOSECONDS)) {
                return false;
            }
            ResT res = toResponse(receivingQueue.remove());
            onMessage(res);
        }
        return true;
    }

    private void cancelFuture(Future<?> future) {
        if (future != null) {
            future.cancel(true);
        }
    }
}
