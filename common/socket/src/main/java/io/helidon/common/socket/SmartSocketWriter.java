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

package io.helidon.common.socket;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.buffers.BufferData;

/**
 * A special socket write that starts async but may switch to sync mode if it
 * detects that the async queue size is below {@link #QUEUE_SIZE_THRESHOLD}.
 * If it switches to sync mode, it shall never return back to async mode.
 *
 * Regular {@link #write(BufferData)} initially enqueues data so callers usually avoid direct
 * socket writes while the queue has capacity. If the async queue stays mostly empty, the writer
 * switches to synchronous writes to avoid the queueing and executor handoff cost on low-contention
 * connections.
 */
public class SmartSocketWriter extends SocketWriter {
    private static final long WINDOW_SIZE = 1000;
    private static final double QUEUE_SIZE_THRESHOLD = 2.0;

    // Owned async writer. Smart close must close this writer because the base close is a no-op.
    private final SocketWriterAsync asyncWriter;
    private final AtomicBoolean closed = new AtomicBoolean();

    /*
     * Serializes SmartSocketWriter entry points. This is required after switching to sync mode
     * because direct socket writes use mutable staging buffers that must not be entered concurrently.
     */
    private final Lock writeLock = new ReentrantLock();

    private volatile long windowIndex;
    private volatile boolean asyncMode;

    SmartSocketWriter(ExecutorService executor, HelidonSocket socket, int writeQueueLength) {
        super(socket);
        this.asyncWriter = new SocketWriterAsync(executor, socket, writeQueueLength);
        this.asyncMode = true;
        this.windowIndex = 0L;
    }

    @Override
    public void write(BufferData... buffers) {
        for (BufferData buffer : buffers) {
            write(buffer);
        }
    }

    @Override
    public void write(BufferData buffer) {
        Objects.requireNonNull(buffer);
        writeLock.lock();
        try {
            checkOpen();
            if (!asyncMode) {
                // Sync mode intentionally bypasses the async writer, but remains serialized by writeLock.
                super.writeNow(buffer);       // blocking write
                return;
            }

            asyncWriter.write(buffer);
            if (++windowIndex % WINDOW_SIZE == 0 && asyncWriter.avgQueueSize() < QUEUE_SIZE_THRESHOLD) {
                // Drain accepted async writes before publishing the one-way transition to sync mode.
                asyncWriter.flush();
                checkOpen();
                asyncMode = false;
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void writeNow(BufferData buffer) {
        Objects.requireNonNull(buffer);
        writeLock.lock();
        try {
            checkOpen();
            if (!asyncMode) {
                super.writeNow(buffer);
                return;
            }

            // In async mode writeNow waits behind queued/in-flight async writes and preserves ordering.
            asyncWriter.writeNow(buffer);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void flush() {
        checkOpen();
        if (!asyncMode) {
            return;
        }

        writeLock.lock();
        try {
            checkOpen();
            if (asyncMode) {
                // Flush must wait for queued async writes before returning to DataWriter callers.
                asyncWriter.flush();
                checkOpen();
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            // Closing the owned async writer wakes blocked producers and attempts to stop the writer thread.
            asyncWriter.close();
        }
    }

    // Intended for deterministic tests of sync-mode close behavior.
    void switchToSyncMode() {
        asyncMode = false;
    }

    // Intended for deterministic tests of full-queue producer sequencing.
    void beforeWriteQueueAwait(Runnable hook) {
        asyncWriter.beforeWriteQueueAwait(hook);
    }

    // Intended for deterministic tests of async flush sequencing.
    void beforeFlushAwait(Runnable hook) {
        asyncWriter.beforeFlushAwait(hook);
    }

    // Intended for deterministic tests of smart-mode transition.
    boolean asyncMode() {
        return asyncMode;
    }

    private void checkOpen() {
        if (closed.get()) {
            throw new SocketWriterException();
        }
    }
}
