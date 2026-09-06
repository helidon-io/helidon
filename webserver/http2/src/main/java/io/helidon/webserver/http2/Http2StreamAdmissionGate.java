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

package io.helidon.webserver.http2;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.locks.LockSupport;

import io.helidon.common.socket.SocketContext;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameListener;

/**
 * Coordinates terminal frame publication with admission of replacement streams.
 *
 * <p>A peer can open a replacement stream as soon as it observes {@code END_STREAM} or {@code RST_STREAM}. At that
 * point, the stream thread may not yet have published the corresponding local state change and deactivated the old
 * stream. When the connection is already at {@code MAX_CONCURRENT_STREAMS}, this gate lets the connection thread wait
 * for one in-flight terminal publication before it rechecks the authoritative active-stream count. A wakeup is only a
 * retry signal; it does not grant stream admission by itself.
 */
final class Http2StreamAdmissionGate implements Http2FrameListener {
    /*
     * Resolve and cache these handles once; no lookup occurs while frames are being written.
     *
     * The connection writer lock serializes updates to started, so that counter needs only a release store. Stream
     * completion can be published by different stream threads, so completed needs an atomic release increment. The
     * connection has a single admission thread, and WAITER uses compare-and-set to register it without losing a
     * concurrent unpark. Acquire reads in awaitPublication make stream-state changes sequenced before completion visible
     * to the connection thread.
     *
     * VarHandles provide these exact memory-ordering operations without locks or extra AtomicLong/AtomicReference holder
     * objects on every connection.
     */
    private static final VarHandle STARTED;
    private static final VarHandle COMPLETED;
    private static final VarHandle WAITER;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            STARTED = lookup.findVarHandle(Http2StreamAdmissionGate.class, "started", long.class);
            COMPLETED = lookup.findVarHandle(Http2StreamAdmissionGate.class, "completed", long.class);
            WAITER = lookup.findVarHandle(Http2StreamAdmissionGate.class, "waiter", Thread.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private long started;
    private long completed;
    private volatile Thread waiter;
    private volatile boolean failed;

    Http2StreamAdmissionGate() {
    }

    @Override
    public void frameHeader(SocketContext ctx, int streamId, Http2FrameHeader header) {
        if (isEndStream(header)) {
            // Mark the publication in flight while Http2ConnectionWriter still owns its connection-wide writer lock.
            STARTED.setRelease(this, started + 1);
        }
    }

    void completePublication() {
        // The caller has already published stream state and deactivation; release that state before waking admission.
        COMPLETED.getAndAddRelease(this, 1L);
        unparkWaiter();
    }

    void fail() {
        failed = true;
        unparkWaiter();
    }

    boolean failed() {
        return failed;
    }

    AwaitResult awaitPublication() {
        // Called only after the active-stream limit check fails. The caller must recheck that count after PUBLISHED.
        if (failed) {
            return AwaitResult.FAILED;
        }

        long observedCompleted = (long) COMPLETED.getAcquire(this);
        if ((long) STARTED.getAcquire(this) == observedCompleted) {
            return failed ? AwaitResult.FAILED : AwaitResult.NO_PENDING;
        }

        Thread currentThread = Thread.currentThread();
        if (!WAITER.compareAndSet(this, null, currentThread)) {
            fail();
            return AwaitResult.FAILED;
        }

        boolean interrupted = false;
        try {
            while (!failed && (long) COMPLETED.getAcquire(this) == observedCompleted) {
                LockSupport.park(this);
                interrupted |= Thread.interrupted();
            }
            return failed ? AwaitResult.FAILED : AwaitResult.PUBLISHED;
        } finally {
            WAITER.compareAndSet(this, currentThread, null);
            if (interrupted) {
                currentThread.interrupt();
            }
        }
    }

    private static boolean isEndStream(Http2FrameHeader header) {
        return switch (header.type()) {
        case DATA, HEADERS -> (header.flags() & Http2Flag.END_OF_STREAM) != 0;
        case RST_STREAM -> true;
        default -> false;
        };
    }

    private void unparkWaiter() {
        Thread waitingThread = waiter;
        if (waitingThread != null) {
            LockSupport.unpark(waitingThread);
        }
    }

    enum AwaitResult {
        PUBLISHED,
        NO_PENDING,
        FAILED
    }
}
