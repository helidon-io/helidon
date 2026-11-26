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

package io.helidon.webserver;

import java.net.SocketTimeoutException;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.socket.SocketWriterException;
import io.helidon.common.socket.TlsNioSocket;

/**
 * Enforces a deadline for each complete transport write.
 */
final class WriteTimeoutSocket implements HelidonSocket {
    private static final Consumer<Runnable> CONTROL_WRITE = Runnable::run;

    private final HelidonSocket delegate;
    private final Consumer<BufferData> bufferWrite;
    private final long timeoutNanos;
    private final ScheduledExecutorService scheduler;
    private final Runnable timeoutAction;
    private final LongSupplier nanoTime;
    private final ReentrantLock lock = new ReentrantLock();
    private final Write primaryWrite = new Write();
    private final Runnable timeoutTask = this::checkTimeout;
    private List<Write> overlappingWrites;
    private ScheduledFuture<?> timeoutFuture;
    private Thread fallbackTimer;
    private boolean primaryWriteActive;
    private boolean timedOut;
    private boolean closing;
    private boolean closed;

    WriteTimeoutSocket(HelidonSocket delegate,
                       Duration timeout,
                       ScheduledExecutorService scheduler,
                       Runnable timeoutAction,
                       LongSupplier nanoTime) {
        this.delegate = delegate;
        this.bufferWrite = delegate::write;
        this.timeoutNanos = timeout.toNanos();
        this.scheduler = scheduler;
        this.timeoutAction = timeoutAction;
        this.nanoTime = nanoTime;
        if (delegate instanceof TlsNioSocket tlsSocket) {
            tlsSocket.controlWriteGuard(this::writeControl);
        }
    }

    @Override
    public void write(BufferData buffer) {
        timedWrite(buffer, bufferWrite, false);
    }

    @Override
    public void close() {
        lock.lock();
        try {
            closing = true;
        } finally {
            lock.unlock();
        }
        boolean closeCompleted = false;
        try {
            // Keep the active write's deadline armed while graceful TLS closure waits for its writer.
            delegate.close();
            closeCompleted = true;
        } finally {
            lock.lock();
            try {
                if (closeCompleted || (!primaryWriteActive
                        && (overlappingWrites == null || overlappingWrites.isEmpty()))) {
                    closed = true;
                    if (timeoutFuture != null) {
                        timeoutFuture.cancel(false);
                        timeoutFuture = null;
                    }
                    if (fallbackTimer != null) {
                        fallbackTimer.interrupt();
                        fallbackTimer = null;
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }

    @Override
    public void idle() {
        delegate.idle();
    }

    @Override
    public boolean isConnected() {
        return delegate.isConnected();
    }

    @Override
    public byte[] get() {
        return delegate.get();
    }

    @Override
    public PeerInfo remotePeer() {
        return delegate.remotePeer();
    }

    @Override
    public PeerInfo localPeer() {
        return delegate.localPeer();
    }

    @Override
    public boolean isSecure() {
        return delegate.isSecure();
    }

    @Override
    public String socketId() {
        return delegate.socketId();
    }

    @Override
    public String childSocketId() {
        return delegate.childSocketId();
    }

    @Override
    public boolean protocolNegotiated() {
        return delegate.protocolNegotiated();
    }

    @Override
    public String protocol() {
        return delegate.protocol();
    }

    private void writeControl(Runnable send) {
        timedWrite(send, CONTROL_WRITE, true);
    }

    private <T> void timedWrite(T value, Consumer<T> action, boolean control) {
        Write write = beginWrite(control);
        RuntimeException failure = null;
        boolean expired;
        try {
            action.accept(value);
        } catch (RuntimeException e) {
            failure = e;
        } finally {
            expired = completeWrite(write);
        }
        if (expired) {
            throw timeoutException(failure);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private SocketWriterException timeoutException(RuntimeException failure) {
        var exception = new SocketTimeoutException("Socket write exceeded " + Duration.ofNanos(timeoutNanos));
        if (failure != null) {
            exception.initCause(failure);
        }
        return new SocketWriterException(exception);
    }

    private Write beginWrite(boolean control) {
        long started = nanoTime.getAsLong();
        lock.lock();
        try {
            if (timedOut) {
                throw timeoutException(null);
            }
            if (closed || (closing && !control)) {
                throw new SocketWriterException(new ClosedChannelException());
            }
            if (timeoutFuture == null && fallbackTimer == null) {
                scheduleTimeout(timeoutNanos - (nanoTime.getAsLong() - started));
            }
            Write write;
            if (!primaryWriteActive) {
                write = primaryWrite;
                primaryWriteActive = true;
            } else {
                write = new Write();
                if (overlappingWrites == null) {
                    overlappingWrites = new ArrayList<>();
                }
                overlappingWrites.add(write);
            }
            write.started = started;
            return write;
        } finally {
            lock.unlock();
        }
    }

    private boolean completeWrite(Write write) {
        boolean abort = false;
        boolean expired;
        lock.lock();
        try {
            if (write == primaryWrite) {
                primaryWriteActive = false;
            } else {
                overlappingWrites.remove(write);
            }
            expired = timedOut || nanoTime.getAsLong() - write.started >= timeoutNanos;
            if (expired && !timedOut) {
                timedOut = true;
                abort = true;
                if (timeoutFuture != null) {
                    timeoutFuture.cancel(false);
                    timeoutFuture = null;
                }
            }
        } finally {
            lock.unlock();
        }
        if (abort) {
            timeoutAction.run();
        }
        return expired;
    }

    private void checkTimeout() {
        lock.lock();
        try {
            timeoutFuture = null;
            if (closed || timedOut) {
                return;
            }
            if (!primaryWriteActive && (overlappingWrites == null || overlappingWrites.isEmpty())) {
                return;
            }
            long remaining = remainingWriteTime();
            if (remaining > 0) {
                try {
                    scheduleTimeout(remaining);
                    return;
                } catch (RejectedExecutionException _) {
                    // Preserve the active write's deadline while the transport scheduler shuts down.
                    fallbackTimer = Thread.ofVirtual()
                            .name("helidon-write-timeout-fallback")
                            .inheritInheritableThreadLocals(false)
                            .start(this::runFallbackTimer);
                    return;
                }
            } else {
                timedOut = true;
            }
        } finally {
            lock.unlock();
        }
        // Closing a channel can linger. Keep the shared deadline scheduler available to other sockets.
        Thread.ofVirtual()
                .name("helidon-write-timeout-abort")
                .inheritInheritableThreadLocals(false)
                .start(timeoutAction);
    }

    private void scheduleTimeout(long remaining) {
        timeoutFuture = scheduler.schedule(timeoutTask, Math.max(0, remaining), TimeUnit.NANOSECONDS);
    }

    private long remainingWriteTime() {
        long remaining = Long.MAX_VALUE;
        long now = nanoTime.getAsLong();
        if (primaryWriteActive) {
            remaining = timeoutNanos - (now - primaryWrite.started);
        }
        if (overlappingWrites != null) {
            for (Write write : overlappingWrites) {
                remaining = Math.min(remaining, timeoutNanos - (now - write.started));
            }
        }
        return remaining;
    }

    private void runFallbackTimer() {
        try {
            while (true) {
                long remaining;
                lock.lock();
                try {
                    if (closed || timedOut || (!primaryWriteActive
                            && (overlappingWrites == null || overlappingWrites.isEmpty()))) {
                        fallbackTimer = null;
                        return;
                    }
                    remaining = remainingWriteTime();
                    if (remaining <= 0) {
                        timedOut = true;
                        break;
                    }
                } finally {
                    lock.unlock();
                }
                TimeUnit.NANOSECONDS.sleep(remaining);
            }
            timeoutAction.run();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        } finally {
            lock.lock();
            try {
                if (fallbackTimer == Thread.currentThread()) {
                    fallbackTimer = null;
                }
            } finally {
                lock.unlock();
            }
        }
    }

    private static final class Write {
        private long started;
    }
}
