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

import java.util.concurrent.ExecutorService;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;

/**
 * Socket writer (possibly) used from multiple threads, takes care of writing to a single
 * socket.
 */
public abstract class SocketWriter implements DataWriter {
    private final HelidonSocket socket;

    /**
     * A new socket writer.
     *
     * @param socket socket to write to
     */
    protected SocketWriter(HelidonSocket socket) {
        this.socket = socket;
    }

    /**
     * Create a new socket writer.
     *
     * @param executor         executor used to create a thread for asynchronous writes
     * @param socket           socket to write to
     * @param writeQueueLength maximal number of queued writes, write operation will block if the queue is full; if set to
     *                         {code 1} or lower, write queue is disabled and writes are direct to socket (blocking)
     * @param smartAsyncWrites flag to enable smart async writes, see {@link io.helidon.common.socket.SmartSocketWriter}
     * @return a new socket writer
     */
    public static SocketWriter create(ExecutorService executor,
                                      HelidonSocket socket,
                                      int writeQueueLength,
                                      boolean smartAsyncWrites) {
        if (writeQueueLength <= 1) {
            return new SocketWriterDirect(socket);
        } else {
            return smartAsyncWrites
                    ? new SmartSocketWriter(executor, socket, writeQueueLength)
                    : new SocketWriterAsync(executor, socket, writeQueueLength);
        }
    }

    /**
     * Create a new socket writer.
     *
     * @param socket           socket to write to
     * @return a new socket writer
     */
    public static SocketWriter create(HelidonSocket socket) {
        return new SocketWriterDirect(socket);
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
     * Does not close the socket.
     */
    public void close() {
    }

    /**
     * Provides access to the underlying socket.
     *
     * @return socket
     */
    protected HelidonSocket socket() {
        return socket;
    }
}
