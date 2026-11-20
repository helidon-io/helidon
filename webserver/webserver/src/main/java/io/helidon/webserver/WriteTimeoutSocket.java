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
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.socket.SocketWriterException;

/**
 * Enforces a deadline for each complete transport write.
 */
final class WriteTimeoutSocket implements HelidonSocket {
    private final HelidonSocket delegate;
    private final long timeoutNanos;
    private final ScheduledExecutorService scheduler;
    private final Runnable timeoutAction;
    private final LongSupplier nanoTime;
    private volatile boolean timedOut;

    WriteTimeoutSocket(HelidonSocket delegate,
                       Duration timeout,
                       ScheduledExecutorService scheduler,
                       Runnable timeoutAction) {
        this(delegate, timeout, scheduler, timeoutAction, System::nanoTime);
    }

    WriteTimeoutSocket(HelidonSocket delegate,
                       Duration timeout,
                       ScheduledExecutorService scheduler,
                       Runnable timeoutAction,
                       LongSupplier nanoTime) {
        this.delegate = delegate;
        this.timeoutNanos = timeout.toNanos();
        this.scheduler = scheduler;
        this.timeoutAction = timeoutAction;
        this.nanoTime = nanoTime;
    }

    @Override
    public void write(BufferData buffer) {
        if (timedOut) {
            throw timeoutException(null);
        }
        var write = new Write(nanoTime.getAsLong());
        long remaining = Math.max(0, timeoutNanos - (nanoTime.getAsLong() - write.started));
        var future = scheduler.schedule(write::expire, remaining, TimeUnit.NANOSECONDS);
        RuntimeException failure = null;
        boolean expired;
        try {
            delegate.write(buffer);
        } catch (RuntimeException e) {
            failure = e;
        } finally {
            expired = write.complete();
            future.cancel(false);
        }
        if (expired) {
            throw timeoutException(failure);
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public void close() {
        delegate.close();
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

    private SocketWriterException timeoutException(RuntimeException failure) {
        var exception = new SocketTimeoutException("Socket write exceeded " + Duration.ofNanos(timeoutNanos));
        if (failure != null) {
            exception.initCause(failure);
        }
        return new SocketWriterException(exception);
    }

    private final class Write {
        private final long started;
        private boolean complete;
        private boolean expired;

        private Write(long started) {
            this.started = started;
        }

        private void expire() {
            synchronized (this) {
                if (complete || expired) {
                    return;
                }
                expired = true;
                timedOut = true;
            }
            // Closing a channel can linger. Keep the shared deadline scheduler available to other sockets.
            Thread.ofVirtual()
                    .name("helidon-write-timeout-abort")
                    .inheritInheritableThreadLocals(false)
                    .start(timeoutAction);
        }

        private boolean complete() {
            synchronized (this) {
                complete = true;
                if (expired) {
                    return true;
                }
                if (nanoTime.getAsLong() - started < timeoutNanos) {
                    return false;
                }
                expired = true;
                timedOut = true;
            }
            timeoutAction.run();
            return true;
        }
    }
}
