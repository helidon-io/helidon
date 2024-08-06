/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.CompositeBufferData;
import io.helidon.common.buffers.DataWriter;

/**
 * Socket writer (possibly) used from multiple threads, takes care of writing to a single
 * socket.
 */
class SocketWriterAsync extends SocketWriter implements DataWriter {
    private static final System.Logger LOGGER = System.getLogger(SocketWriterAsync.class.getName());
    private static final BufferData CLOSING_TOKEN = BufferData.empty();
    private static final int QUEUE_SIZE_THRESHOLD = 2;
    private static final int SMART_QUEUE_TIMER_MILLIS = 2000;

    private final ExecutorService executor;
    private final ArrayBlockingQueue<BufferData> writeQueue;
    private final CountDownLatch cdl = new CountDownLatch(1);
    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile Throwable caught;
    private volatile boolean run = true;
    private Thread thread;
    private double avgQueueSize;
    private final AtomicBoolean suspendedQueue = new AtomicBoolean(false);

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
        this.writeQueue = new ArrayBlockingQueue<>(writeQueueLength);
    }

    @Override
    public void write(BufferData... buffers) {
        for (BufferData buffer : buffers) {
            write(buffer);
        }
    }

    @Override
    public void write(BufferData buffer) {
        // if queue suspended, switch to sync writes
        if (suspendedQueue.get()) {
            drainQueueMaybe();
            writeNow(buffer);
            return;
        }

        // proceed with async writes
        checkRunning();
        try {
            if (!writeQueue.offer(buffer, 10, TimeUnit.SECONDS)) {
                checkRunning();
                throw new IllegalStateException("Failed to write data to queue, timed out");
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted while trying to write to a queue", e);
        }
    }

    /**
     * Close this writer. Will attempt to write all enqueued buffers and will stop the thread if created.
     */
    public void close() {
        run = false;

        // if queue suspended, drain and return
        if (suspendedQueue.get()) {
            drainQueueMaybe();
            return;
        }

        // if not started return
        if (!started.get()) {
            // thread never started
            return;
        }

        try {
            writeQueue.put(CLOSING_TOKEN); // wake up blocked take() operation
            if (cdl.await(1000, TimeUnit.MILLISECONDS)) {
                // reads finished because we set run to false
                BufferData available;
                while ((available = writeQueue.poll()) != null) {
                    try {
                        writeNow(available);
                    } catch (Exception e) {
                        LOGGER.log(System.Logger.Level.TRACE, "Failed to write last buffers during writer shutdown", e);
                        // in case we fail to write to socket when closing, it is probably because it is already closed
                        // we still need to release all buffers
                    }
                }
            }
            if (thread != null) {
                // fail blocked writers
                thread.interrupt();
            }
        } catch (InterruptedException e) {            // failed to get
        }

    }

    private void run() {
        this.thread = Thread.currentThread();
        this.thread.setName("[" + socket().socketId() + " " + socket().childSocketId() + "]");
        try {
            long startTimeMillis = System.currentTimeMillis();
            while (run) {
                CompositeBufferData toWrite = BufferData.createComposite(writeQueue.take());  // wait if the queue is empty
                // we only want to read a certain amount of data, if somebody writes huge amounts
                // we could spin here forever and run out of memory
                int queueSize = 1;
                for (; queueSize <= 1000; queueSize++) {
                    BufferData newBuf = writeQueue.poll(); // drain ~all elements from the queue, don't wait.
                    if (newBuf == null) {
                        break;
                    }
                    toWrite.add(newBuf);
                }
                writeNow(toWrite);

                avgQueueSize = (avgQueueSize + queueSize) / 2.0;
                if (System.currentTimeMillis() - startTimeMillis > SMART_QUEUE_TIMER_MILLIS) {
                    if (avgQueueSize < QUEUE_SIZE_THRESHOLD) {
                        suspendedQueue.set(true);
                        break;
                    }
                }
            }
            cdl.countDown();
        } catch (Throwable e) {
            this.caught = e;
            this.run = false;
        }
    }

    private void checkRunning() {
        if (started.compareAndSet(false, true)) {
            // start writer on first asynchronous write
            executor.submit(this::run);
        }
        if (!run) {
            throw new SocketWriterException(caught);
        }
    }

    private void drainQueueMaybe() {
        BufferData buffer;
        while ((buffer = writeQueue.poll()) != null) {
            writeNow(buffer);
        }
    }
}
