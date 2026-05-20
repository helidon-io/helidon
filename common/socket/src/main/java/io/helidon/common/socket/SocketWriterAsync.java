/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.CompositeBufferData;
import io.helidon.common.buffers.DataWriter;

/**
 * Socket writer (possibly) used from multiple threads, takes care of writing to a single
 * socket.
 *
 * The writer has two write paths:
 * <ul>
 *     <li>{@link #write(BufferData)} enqueues data for later draining.</li>
 *     <li>{@link #writeNow(BufferData)} and {@link #flush()} drain queued data on the caller thread.</li>
 * </ul>
 * The caller-thread flush path is intentional: when the caller and writer use the same executor,
 * waiting for the writer thread to flush could deadlock under saturation or reentrant use.
 */
class SocketWriterAsync extends SocketWriter implements DataWriter {
    private static final Runnable EMPTY_RUNNABLE = () -> { };
    private static final long CLOSE_TIMEOUT_MILLIS = 1000;
    private static final int MAX_BATCH_SIZE = 1000;
    private static final long WRITE_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(10);

    private final ExecutorService executor;
    private final Deque<BufferData> writeQueue;
    private final BufferData[] writeBatch;
    private final int writeQueueLength;
    private final CountDownLatch cdl = new CountDownLatch(1);
    private final AtomicBoolean started = new AtomicBoolean(false);

    /*
     * All queue state is protected by stateLock. The conditions split the common waits:
     * - notEmpty wakes the writer thread when producers enqueue data.
     * - notFull wakes blocked producers when a batch is drained.
     * - idle wakes either side when an active socket write or flush finishes.
     */
    private final Lock stateLock = new ReentrantLock();
    private final Condition notEmpty = stateLock.newCondition();
    private final Condition notFull = stateLock.newCondition();
    private final Condition idle = stateLock.newCondition();

    // Test hooks used to block at deterministic points without exposing production state.
    private volatile Runnable beforeCloseAwait = EMPTY_RUNNABLE;
    private volatile Runnable beforeFlushAwait = EMPTY_RUNNABLE;
    private volatile Runnable beforeWriteQueueAwait = EMPTY_RUNNABLE;

    // Failure and lifecycle fields are read from paths that may not hold stateLock.
    private volatile Throwable caught;
    private volatile boolean run = true;
    private volatile Thread thread;
    private volatile double avgQueueSize;

    // Guarded by stateLock. Exactly one thread may be writing to the socket at a time.
    private boolean writing;

    // Guarded by stateLock. While true, producers wait so later writes cannot overtake flush/writeNow.
    private boolean flushing;

    /**
     * A new socket writer.
     *
     * @param executor         executor used to create a thread for asynchronous writes
     * @param socket           socket to write to
     * @param writeQueueLength maximal number of queued writes, write operation will block if the queue is full; if set to
     *                         {code 1} or lower, write queue is disabled and writes are direct to socket (blocking)
     */
    SocketWriterAsync(ExecutorService executor, HelidonSocket socket, int writeQueueLength) {
        super(socket);
        this.executor = executor;
        this.writeQueue = new ArrayDeque<>(writeQueueLength);
        this.writeBatch = new BufferData[Math.min(writeQueueLength, MAX_BATCH_SIZE)];
        this.writeQueueLength = writeQueueLength;
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
        long remaining = WRITE_TIMEOUT_NANOS;

        // Start outside stateLock so executor submission cannot block queue state progress.
        startWriterIfNeeded();
        try {
            stateLock.lockInterruptibly();
            try {
                checkRunning();

                // Producers that observe an active flush wait so they do not overtake the flush/writeNow path.
                while (caught == null && run && flushing) {
                    idle.await();
                    checkRunning();
                }

                // Apply bounded backpressure when the queue is full.
                while (writeQueue.size() == writeQueueLength) {
                    checkRunning();
                    beforeWriteQueueAwait.run();
                    remaining = notFull.awaitNanos(remaining);
                    if (remaining > 0) {
                        continue;
                    }
                    checkRunning();
                    throw new IllegalStateException("Failed to write data to queue, timed out");
                }
                checkRunning();
                writeQueue.add(buffer);
                notEmpty.signal();
            } finally {
                stateLock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while trying to write to a queue", e);
        }
    }

    @Override
    public void writeNow(BufferData buffer) {
        flush(Objects.requireNonNull(buffer));
    }

    @Override
    public void flush() {
        flush(null);
    }

    private void flush(BufferData current) {
        boolean currentPending = current != null;
        boolean runHook = true;
        boolean flushStarted = false;
        try {
            while (true) {
                int queueSize;
                stateLock.lockInterruptibly();
                try {
                    // Nothing has ever been scheduled and there is no current writeNow buffer.
                    if (!flushStarted && !currentPending && !started.get() && !writing && writeQueue.isEmpty()) {
                        return;
                    }
                    if (!flushStarted) {
                        // Only one caller-thread flush can own the queue at a time.
                        while (caught == null && run && flushing) {
                            idle.await();
                        }
                        checkRunning();
                        flushing = true;
                        flushStarted = true;
                    }
                    if (runHook) {
                        beforeFlushAwait.run();
                        runHook = false;
                    }

                    // Do not write to the socket concurrently with the writer thread or another flush iteration.
                    while (caught == null && run && writing) {
                        idle.await();
                    }
                    checkRunning();

                    /*
                     * Queue contents always go first. For writeNow(current), this preserves FIFO ordering for queued
                     * data and for producers that were already waiting for queue capacity before this flush started.
                     */
                    if (!writeQueue.isEmpty()) {
                        queueSize = drainBatch(writeBatch.length);
                    } else if (currentPending) {
                        writeBatch[0] = current;
                        currentPending = false;
                        queueSize = 1;
                    } else {
                        return;
                    }

                    // The lock is released while writing to the socket, but the writing flag keeps other writers out.
                    writing = true;
                } finally {
                    stateLock.unlock();
                }

                try {
                    writeBatch(queueSize);
                } catch (Throwable e) {
                    fail(e);
                    throw socketWriterException();
                } finally {
                    stateLock.lock();
                    try {
                        writing = false;
                        idle.signalAll();
                    } finally {
                        stateLock.unlock();
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while trying to flush queued writes", e);
        } finally {
            if (flushStarted) {
                stateLock.lock();
                try {
                    // Release producers and the writer thread after the caller-thread flush is fully done.
                    flushing = false;
                    idle.signalAll();
                } finally {
                    stateLock.unlock();
                }
            }
        }
    }

    /**
     * Close this writer, wake blocked waiters, and stop the writer thread if created.
     * Queued buffers may be discarded during close.
     */
    public void close() {
        boolean threadStarted;
        stateLock.lock();
        try {
            // Stop accepting new data and wake blocked producers, flushers, and the writer so they can observe close.
            run = false;
            threadStarted = started.get();
            notEmpty.signalAll();
            notFull.signalAll();
            idle.signalAll();
        } finally {
            stateLock.unlock();
        }
        if (!threadStarted) {
            // thread never started
            return;
        }

        try {
            beforeCloseAwait.run();
            boolean stopped = cdl.await(CLOSE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            if (!stopped) {
                // The socket may be stuck in I/O; interrupt and discard buffers instead of blocking close forever.
                interruptWriter();
                discardQueue();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            interruptWriter();
            discardQueue();
        }

    }

    // Intended for deterministic tests of close sequencing.
    void beforeCloseAwait(Runnable hook) {
        this.beforeCloseAwait = Objects.requireNonNull(hook);
    }

    // Intended for deterministic tests of flush sequencing.
    void beforeFlushAwait(Runnable hook) {
        this.beforeFlushAwait = Objects.requireNonNull(hook);
    }

    // Intended for deterministic tests of full-queue producer sequencing.
    void beforeWriteQueueAwait(Runnable hook) {
        this.beforeWriteQueueAwait = Objects.requireNonNull(hook);
    }

    private void interruptWriter() {
        Thread currentThread = thread;
        if (currentThread != null) {
            currentThread.interrupt();
        }
    }

    private boolean writeNextQueued() throws InterruptedException {
        int queueSize;

        stateLock.lockInterruptibly();
        try {
            // The background writer yields to caller-thread flush/writeNow so those calls can make progress.
            while (writeQueue.isEmpty() || writing || flushing) {
                if (!run) {
                    return false;
                }
                if (writing || flushing) {
                    idle.await();
                } else {
                    notEmpty.await();
                }
            }

            queueSize = drainBatch(writeBatch.length);

            // The actual socket write happens outside stateLock; this flag keeps all other writers out.
            writing = true;
        } finally {
            stateLock.unlock();
        }

        try {
            writeBatch(queueSize);
            return true;
        } catch (Throwable e) {
            fail(e);
            return false;
        } finally {
            stateLock.lock();
            try {
                writing = false;
                idle.signalAll();
            } finally {
                stateLock.unlock();
            }
        }
    }

    private void fail(Throwable e) {
        stateLock.lock();
        try {
            // Record the terminal failure and wake every waiter so they all observe it.
            this.caught = e;
            this.run = false;
            notEmpty.signalAll();
            notFull.signalAll();
            idle.signalAll();
        } finally {
            stateLock.unlock();
        }
    }

    private void run() {
        this.thread = Thread.currentThread();
        this.thread.setName("[" + socket().socketId() + " " + socket().childSocketId() + "]");
        try {
            // Drain queued writes until close or failure tells writeNextQueued to stop.
            while (writeNextQueued()) {
            }
        } catch (Throwable e) {
            fail(e);
        } finally {
            // Drop queued buffer references on any terminal exit, including close and failed socket writes.
            discardQueue();
            cdl.countDown();
        }
    }

    private void checkRunning() {
        if (!run) {
            throw socketWriterException();
        }
    }

    private SocketWriterException socketWriterException() {
        Throwable failure = caught;
        return failure == null ? new SocketWriterException() : new SocketWriterException(failure);
    }

    private void startWriterIfNeeded() {
        if (started.compareAndSet(false, true)) {
            try {
                // start writer on first asynchronous write
                executor.submit(this::run);
            } catch (RuntimeException e) {
                fail(e);
                cdl.countDown();
                throw e;
            }
        }
    }

    double avgQueueSize() {
        return avgQueueSize;
    }

    // Intended for deterministic tests of close cleanup.
    int queuedBufferCount() {
        stateLock.lock();
        try {
            return writeQueue.size();
        } finally {
            stateLock.unlock();
        }
    }

    private void signalNotFull(int slots) {
        for (int i = 0; i < slots; i++) {
            notFull.signal();
        }
    }

    private int drainBatch(int maxSize) {
        // Caller must hold stateLock.
        int queueSize = 0;
        while (queueSize < maxSize && !writeQueue.isEmpty()) {
            writeBatch[queueSize++] = writeQueue.remove();
        }
        signalNotFull(queueSize);
        return queueSize;
    }

    private void writeBatch(int queueSize) {
        // Caller must have reserved the socket write by setting writing=true.
        CompositeBufferData toWrite = BufferData.createComposite(writeBatch[0]);
        writeBatch[0] = null;
        for (int i = 1; i < queueSize; i++) {
            toWrite.add(writeBatch[i]);
            writeBatch[i] = null;
        }
        super.writeNow(toWrite);
        avgQueueSize = (avgQueueSize + queueSize) / 2.0;
    }

    private void discardQueue() {
        stateLock.lock();
        try {
            // Clearing the queue drops references and unblocks producers.
            writeQueue.clear();
            notFull.signalAll();
            idle.signalAll();
        } finally {
            stateLock.unlock();
        }
    }
}
