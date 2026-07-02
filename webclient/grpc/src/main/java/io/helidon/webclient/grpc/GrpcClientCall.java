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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.buffers.BufferData;
import io.helidon.grpc.core.GrpcHeadersUtil;
import io.helidon.http.Headers;
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2Exception;
import io.helidon.http.http2.Http2Headers;
import io.helidon.webclient.http2.StreamTimeoutException;

import io.grpc.CallOptions;
import io.grpc.InternalStatus;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

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
    private static final long MAX_QUEUED_BYTES = 64 * 1024;
    private static final ThreadFactory READ_THREAD_FACTORY = Thread.ofVirtual()
            .name("helidon-grpc-client-read-", 0)
            .factory();
    private static final ThreadFactory HEARTBEAT_DISPATCH_THREAD_FACTORY = Thread.ofVirtual()
            .name("helidon-grpc-heartbeat-dispatch-", 0)
            .factory();
    private static final ScheduledThreadPoolExecutor HEARTBEAT_SCHEDULER;

    static {
        HEARTBEAT_SCHEDULER = new ScheduledThreadPoolExecutor(1,
                                                             Thread.ofPlatform()
                                                                     .daemon(true)
                                                                     .name("helidon-grpc-heartbeat")
                                                                     .factory());
        HEARTBEAT_SCHEDULER.setRemoveOnCancelPolicy(true);
    }

    private final ExecutorService executor;
    private final Semaphore messageRequest = new Semaphore(0);

    private final LinkedBlockingQueue<BufferData> sendingQueue = new LinkedBlockingQueue<>();
    private final AtomicLong queuedBytes = new AtomicLong();
    private final AtomicBoolean terminal = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean heartbeatStarted = new AtomicBoolean();
    private final ReentrantLock demandLock = new ReentrantLock();
    private final ReentrantLock heartbeatLock = new ReentrantLock();
    private final ReentrantLock listenerLock = new ReentrantLock();
    private final ReentrantLock writerLock = new ReentrantLock();

    private final CountDownLatch startReadBarrier = new CountDownLatch(1);

    private volatile Future<?> readStreamFuture;
    private volatile Thread readStreamThread;
    private volatile Future<?> writeStreamFuture;
    private volatile ScheduledFuture<?> heartbeatFuture;
    private volatile RuntimeException resetFailure;
    private boolean writerScheduled;

    GrpcClientCall(GrpcChannel grpcChannel, MethodDescriptor<ReqT, ResT> methodDescriptor, CallOptions callOptions) {
        super(grpcChannel, methodDescriptor, callOptions);
        this.executor = grpcClient().prototype().executor();
    }

    @Override
    public void request(int numMessages) {
        socket().log(LOGGER, DEBUG, "request called %d", numMessages);
        demandLock.lock();
        try {
            int available = messageRequest.availablePermits();
            messageRequest.release(Math.min(numMessages, Integer.MAX_VALUE - available));
        } finally {
            demandLock.unlock();
        }
        startReadBarrier.countDown();
    }

    @Override
    public void cancel(String message, Throwable cause) {
        socket().log(LOGGER, DEBUG, "cancel called %s", message);
        if (!terminal.get()) {
            try {
                notifyClosed(Status.CANCELLED, EMPTY_METADATA);
            } finally {
                close();
            }
        }
    }

    @Override
    public void halfClose() {
        socket().log(LOGGER, DEBUG, "halfClose called");
        sendingQueue.add(EMPTY_BUFFER_DATA);       // end marker
        scheduleWriter();
        startHeartbeat();
    }

    @Override
    public void sendMessage(ReqT message) {
        // serialize and queue message for writing
        byte[] serialized = serializeMessage(message);
        BufferData messageData = BufferData.createReadOnly(serialized, 0, serialized.length);
        BufferData headerData = BufferData.create(DATA_PREFIX_LENGTH);
        headerData.writeInt8(0);                                // no compression
        headerData.writeUnsignedInt32(messageData.available());         // length prefixed
        BufferData data = BufferData.create(headerData, messageData);
        queuedBytes.addAndGet(data.available());
        sendingQueue.add(data);
        scheduleWriter();
        startHeartbeat();
    }

    @Override
    public boolean isReady() {
        return !terminal.get() && isRemoteOpen() && queuedBytes.get() < MAX_QUEUED_BYTES;
    }

    protected void startStreamingThreads() {
        clientStream().onReset(rstStream -> {
            resetFailure = new Http2Exception(rstStream.errorCode(),
                                              "Reset of " + clientStream().streamId() + " stream received");
            demandLock.lock();
            try {
                if (messageRequest.availablePermits() == 0) {
                    messageRequest.release();
                }
            } finally {
                demandLock.unlock();
            }
            startReadBarrier.countDown();
        });
        if (heartbeatPeriod().compareTo(Duration.ZERO) <= 0) {
            heartbeatStarted.set(true);
        }
        FutureTask<Void> readTask = new FutureTask<>(() -> {
            try {
                startReadBarrier.await();
                socket().log(LOGGER, DEBUG, "[Reading thread] started");
                Status status = Status.OK;
                Metadata trailingMetadata = EMPTY_METADATA;
                while (true) {
                    try {
                        Http2Headers headers = clientStream().readHeaders();
                        if (headers.httpHeaders().contains(STATUS_NAME)) {
                            trailingMetadata = GrpcHeadersUtil.toMetadata(headers);
                            status = status(status, trailingMetadata);
                        }
                        break;
                    } catch (StreamTimeoutException e) {
                        handleStreamTimeout(e);
                    } catch (IllegalStateException e) {
                        RuntimeException resetFailure = this.resetFailure;
                        if (resetFailure != null) {
                            throw resetFailure;
                        }
                        throw e;
                    }
                }
                Duration requestWaitTime = grpcClient().prototype().protocolConfig().nextRequestWaitTime();
                while (true) {
                    if (resetFailure != null) {
                        throw resetFailure;
                    }
                    if (clientStream().trailers().isDone() || !clientStream().hasEntity()) {
                        socket().log(LOGGER, DEBUG, "[Reading thread] trailers or eos received");
                        break;
                    }
                    BufferData bufferData = readGrpcFrame();
                    if (bufferData == null) {
                        continue;
                    }
                    if (enableMetrics()) {
                        bytesRcvd().addAndGet(bufferData.available() - DATA_PREFIX_LENGTH);
                    }
                    if (!messageRequest.tryAcquire(requestWaitTime.toNanos(), TimeUnit.NANOSECONDS)) {
                        socket().log(LOGGER, DEBUG, "[Reading thread] response demand timed out");
                        notifyClosed(Status.CANCELLED);
                        return;
                    }
                    if (resetFailure != null) {
                        throw resetFailure;
                    }
                    notifyMessage(toResponse(bufferData));
                }
                if (clientStream().trailers().isDone()) {
                    Headers trailers = clientStream().trailers().get();
                    trailingMetadata = GrpcHeadersUtil.toMetadata(trailers);
                    status = status(status, trailingMetadata);
                }
                notifyClosed(status, trailingMetadata);
            } catch (StreamTimeoutException e) {
                notifyClosed(Status.DEADLINE_EXCEEDED);
            } catch (Http2Exception e) {
                socket().log(LOGGER, ERROR, e.getMessage(), e);
                notifyClosed(resetStatus(e.code()).withDescription(e.getMessage()).withCause(e));
            } catch (Throwable e) {
                socket().log(LOGGER, ERROR, e.getMessage(), e);
                notifyClosed(Status.UNKNOWN.withDescription(e.getMessage()).withCause(e));
            } finally {
                close();
            }
            socket().log(LOGGER, DEBUG, "[Reading thread] exiting");
        }, null);
        readStreamFuture = readTask;
        if (closed.get()) {
            readTask.cancel(true);
        } else {
            Thread readThread = READ_THREAD_FACTORY.newThread(readTask);
            readStreamThread = readThread;
            readThread.start();
        }
    }

    private void startHeartbeat() {
        Duration period = heartbeatPeriod();
        if (period.compareTo(Duration.ZERO) <= 0 || !heartbeatStarted.compareAndSet(false, true)) {
            return;
        }
        socket().log(LOGGER, DEBUG, "[Heartbeat] started with period " + period);
        scheduleHeartbeat(period);
    }

    private void scheduleHeartbeat(Duration period) {
        heartbeatLock.lock();
        try {
            if (closed.get()) {
                return;
            }
            heartbeatFuture = HEARTBEAT_SCHEDULER.schedule(() -> {
                if (closed.get()) {
                    return;
                }
                HEARTBEAT_DISPATCH_THREAD_FACTORY.newThread(() -> {
                    try {
                        executor.execute(() -> {
                            if (closed.get() || !isRemoteOpen()) {
                                return;
                            }
                            if (sendingQueue.isEmpty()) {
                                sendingQueue.add(PING_FRAME);
                                scheduleWriter();
                            }
                            scheduleHeartbeat(period);
                        });
                    } catch (RuntimeException | Error t) {
                        try {
                            notifyClosed(Status.UNKNOWN.withDescription(t.getMessage()).withCause(t));
                        } finally {
                            close();
                        }
                    }
                }).start();
            }, period.toNanos(), TimeUnit.NANOSECONDS);
        } finally {
            heartbeatLock.unlock();
        }
    }

    private void scheduleWriter() {
        FutureTask<Void> task;
        writerLock.lock();
        try {
            if (closed.get()) {
                sendingQueue.clear();
                queuedBytes.set(0);
                return;
            }
            if (writerScheduled) {
                return;
            }
            if (!isRemoteOpen()) {
                sendingQueue.clear();
                queuedBytes.set(0);
                return;
            }
            task = new FutureTask<>(() -> {
                writeQueued();
                return null;
            });
            writerScheduled = true;
            writeStreamFuture = task;
        } finally {
            writerLock.unlock();
        }
        try {
            executor.execute(task);
        } catch (RuntimeException | Error t) {
            writerLock.lock();
            try {
                if (writeStreamFuture == task) {
                    writerScheduled = false;
                    writeStreamFuture = null;
                }
            } finally {
                writerLock.unlock();
            }
            try {
                notifyClosed(Status.UNKNOWN.withDescription(t.getMessage()).withCause(t));
            } finally {
                close();
            }
        }
    }

    private void writeQueued() {
        try {
            socket().log(LOGGER, DEBUG, "[Writing task] started");
            boolean endOfStream = false;
            while (isRemoteOpen()) {
                BufferData bufferData = sendingQueue.poll();
                if (bufferData == null) {
                    return;
                }
                if (bufferData == PING_FRAME) {
                    clientStream().sendPing();
                    continue;
                }
                if (bufferData == EMPTY_BUFFER_DATA) {
                    if (!endOfStream) {
                        clientStream().writeData(EMPTY_BUFFER_DATA, true);
                    }
                    return;
                }
                endOfStream = sendingQueue.peek() == EMPTY_BUFFER_DATA;
                int writeLength = bufferData.available();
                socket().log(LOGGER, DEBUG, "[Writing task] writing bufferData %b", endOfStream);
                boolean written = false;
                try {
                    clientStream().writeData(bufferData, endOfStream);
                    written = true;
                    if (enableMetrics()) {
                        bytesSent().addAndGet(writeLength);
                    }
                } finally {
                    long before = queuedBytes.getAndUpdate(current -> Math.max(0, current - writeLength));
                    if (written && before >= MAX_QUEUED_BYTES && before - writeLength < MAX_QUEUED_BYTES) {
                        listenerLock.lock();
                        try {
                            if (!terminal.get()) {
                                responseListener().onReady();
                            }
                        } finally {
                            listenerLock.unlock();
                        }
                    }
                }
            }
        } catch (Throwable e) {
            socket().log(LOGGER, ERROR, e.getMessage(), e);
            try {
                notifyClosed(Status.UNKNOWN.withDescription(e.getMessage()).withCause(e));
            } finally {
                close();
            }
        } finally {
            boolean reschedule;
            writerLock.lock();
            try {
                writerScheduled = false;
                writeStreamFuture = null;
                reschedule = !closed.get() && isRemoteOpen() && !sendingQueue.isEmpty();
            } finally {
                writerLock.unlock();
            }
            if (reschedule) {
                scheduleWriter();
            } else if (!isRemoteOpen()) {
                sendingQueue.clear();
                queuedBytes.set(0);
            }
            socket().log(LOGGER, DEBUG, "[Writing task] exiting");
        }
    }

    private void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        socket().log(LOGGER, DEBUG, "closing client call");
        try {
            sendingQueue.clear();
            queuedBytes.set(0);
            try {
                if (resetFailure == null) {
                    clientStream().cancel();
                } else {
                    clientStream().close();
                }
            } finally {
                try {
                    connection().close();
                } finally {
                    unblockUnaryExecutor();
                }
            }

            // update metrics
            if (enableMetrics()) {
                MethodMetrics methodMetrics = methodMetrics();
                methodMetrics.callDuration().record(
                        Duration.ofMillis(System.currentTimeMillis() - startMillis()));
                methodMetrics.recvMessageSize().record(bytesRcvd().get());
                methodMetrics.sentMessageSize().record(bytesSent().get());
            }
        } finally {
            Future<?> readStreamFuture = this.readStreamFuture;
            if (readStreamFuture != null) {
                readStreamFuture.cancel(true);
            }
            Future<?> writeStreamFuture = this.writeStreamFuture;
            if (writeStreamFuture != null) {
                writeStreamFuture.cancel(true);
            }
            heartbeatLock.lock();
            try {
                ScheduledFuture<?> heartbeatFuture = this.heartbeatFuture;
                if (heartbeatFuture != null) {
                    heartbeatFuture.cancel(true);
                }
            } finally {
                heartbeatLock.unlock();
            }
        }
    }

    private void notifyClosed(Status status) {
        notifyClosed(status, EMPTY_METADATA);
    }

    private static Status status(Status fallback, Metadata metadata) {
        Status status = metadata.get(InternalStatus.CODE_KEY);
        String description = metadata.get(InternalStatus.MESSAGE_KEY);
        metadata.discardAll(InternalStatus.CODE_KEY);
        metadata.discardAll(InternalStatus.MESSAGE_KEY);
        status = status == null ? fallback : status;
        return description == null ? status : status.withDescription(description);
    }

    static Status resetStatus(Http2ErrorCode errorCode) {
        return switch (errorCode) {
        case REFUSED_STREAM -> Status.UNAVAILABLE;
        case CANCEL -> Status.CANCELLED;
        case ENHANCE_YOUR_CALM -> Status.RESOURCE_EXHAUSTED;
        case INADEQUATE_SECURITY -> Status.PERMISSION_DENIED;
        case HTTP_1_1_REQUIRED -> Status.UNKNOWN;
        default -> Status.INTERNAL;
        };
    }

    private boolean notifyClosed(Status status, Metadata metadata) {
        if (terminal.compareAndSet(false, true)) {
            listenerLock.lock();
            try {
                responseListener().onClose(status, metadata);
            } finally {
                listenerLock.unlock();
            }
            return true;
        }
        return false;
    }

    private void notifyMessage(ResT response) {
        listenerLock.lock();
        try {
            if (!terminal.get()) {
                responseListener().onMessage(response);
            }
        } finally {
            listenerLock.unlock();
        }
    }

    // Package-private lifecycle accessors are test seams for deterministic task cleanup assertions.
    boolean readTaskTerminated() {
        Thread readStreamThread = this.readStreamThread;
        return readStreamThread == null || !readStreamThread.isAlive();
    }

    boolean heartbeatTaskPending() {
        heartbeatLock.lock();
        try {
            ScheduledFuture<?> heartbeatFuture = this.heartbeatFuture;
            return heartbeatFuture != null && !heartbeatFuture.isDone();
        } finally {
            heartbeatLock.unlock();
        }
    }

    boolean heartbeatTaskCancelled() {
        heartbeatLock.lock();
        try {
            ScheduledFuture<?> heartbeatFuture = this.heartbeatFuture;
            return heartbeatFuture != null && heartbeatFuture.isCancelled();
        } finally {
            heartbeatLock.unlock();
        }
    }
}
