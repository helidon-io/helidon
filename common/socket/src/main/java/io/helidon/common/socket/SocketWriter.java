/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
public class SocketWriter implements DataWriter {
    private static final System.Logger LOGGER = System.getLogger(SocketWriter.class.getName());
    private static final BufferData CLOSING_TOKEN = BufferData.empty();
    private final ExecutorService executor;
    private final HelidonSocket socket;
    private final ArrayBlockingQueue<BufferData> writeQueue;
    private final CountDownLatch cdl = new CountDownLatch(1);
    private final String channelId;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile Throwable caught;
    private volatile boolean run = true;
    private Thread thread;

    /**
     * A new socket writer.
     *
     * @param executor executor used to create a thread for asynchronous writes
     * @param socket socket to write to
     * @param channelId channel id of this connection
     * @param writeQueueLength maximal number of queued writes, write operation will block if the queue is full
     */
    public SocketWriter(ExecutorService executor, HelidonSocket socket, String channelId, int writeQueueLength) {
        this.executor = executor;
        this.socket = socket;
        this.channelId = channelId;
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

    @Override
    public void writeNow(BufferData... buffers) {
        BufferData composite = BufferData.create(buffers);
        writeNow(composite);
    }

    @Override
    public void writeNow(BufferData buffer) {
        socket.write(buffer);
    }

    /**
     * Close this writer. Will attempt to write all enqueued buffers and will stop the thread if created.
     */
    public void close() {
        run = false;
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
        try {
            while (run) {
                CompositeBufferData toWrite = BufferData.createComposite(writeQueue.take());  // wait if the queue is empty
                // we only want to read a certain amount of data, if somebody writes huge amounts
                // we could spin here forever and run out of memory
                for (int i = 0; i < 1000; i++) {
                    BufferData newBuf = writeQueue.poll(); // drain ~all elements from the queue, don't wait.
                    if (newBuf == null) {
                        break;
                    }
                    toWrite.add(newBuf);
                }
                writeNow(toWrite);
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
}
