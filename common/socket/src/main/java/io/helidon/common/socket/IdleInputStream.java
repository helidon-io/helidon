/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.helidon.common.LazyValue;

/**
 * InputStream capable of cheap continuous reading of single byte from inactive socket input stream
 * in order to detect blocking socket disconnection.
 * Needs to be switched to idle monitoring mode typically when client connection is cached.
 * <p>
 * Returns automatically to standard mode when read method is executed.
 */
class IdleInputStream extends InputStream {

    private final InputStream upstream;
    private final LazyValue<ExecutorService> executor;
    private volatile int next = -1;
    private volatile boolean closed = false;
    private Future<?> idlingThread;

    IdleInputStream(InputStream upstream, String childSocketId, String socketId) {
        this.upstream = upstream;
        executor = LazyValue.create(() -> Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual()
                        .name("helidon-socket-monitor-" + childSocketId + "-" + socketId, 0)
                        .factory())
        );
    }

    @Override
    public int read() throws IOException {
        if (idlingThread != null) {
            endIdle();
        }
        if (next < 0) {
            return upstream.read();
        } else {
            int res = next;
            next = -1;
            return res;
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (idlingThread != null) {
            endIdle();
        }
        if (next < 0) {
            return upstream.read(b, off, len);
        } else {
            Objects.checkFromIndexSize(off, len, b.length);
            if (len == 0) {
                return 0;
            }
            b[off] = (byte) next;
            next = -1;
            return 1;
        }
    }

    @Override
    public void close() throws IOException {
        upstream.close();
        closed = true;
    }

    /**
     * Enable idle mode, connection is expected to be idle,
     * single byte will be read asynchronously
     * in blocking manner to detect severed connection.
     */
    void idle() {
        if (idlingThread != null) {
            return;
        }
        idlingThread = executor.get().submit(this::handle);
    }

    boolean isClosed() {
        return closed;
    }

    private void handle() {
        try {
            next = upstream.read();
            if (next <= 0) {
                closed = true;
            }
        } catch (IOException e) {
            closed = true;
            throw new UncheckedIOException(e);
        }
    }

    private void endIdle() {
        try {
            idlingThread.get();
            idlingThread = null;
        } catch (InterruptedException | ExecutionException e) {
            closed = true;
            throw new RuntimeException("Exception in socket monitor thread.", e);
        }
    }
}
