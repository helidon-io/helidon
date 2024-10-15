/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

/**
 * A special socket write that starts async but may switch to sync mode if it
 * detects that the async queue size is below {@link #QUEUE_SIZE_THRESHOLD}.
 * If it switches to sync mode, it shall never return back to async mode.
 */
public class SmartSocketWriter extends SocketWriter {
    private static final long WINDOW_SIZE = 1000;
    private static final double QUEUE_SIZE_THRESHOLD = 2.0;

    private final SocketWriterAsync asyncWriter;
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
        if (asyncMode) {
            asyncWriter.write(buffer);
            if (++windowIndex % WINDOW_SIZE == 0 && asyncWriter.avgQueueSize() < QUEUE_SIZE_THRESHOLD) {
                asyncMode = false;
            }
        } else {
            asyncWriter.drainQueue();
            writeNow(buffer);       // blocking write
        }
    }
}
