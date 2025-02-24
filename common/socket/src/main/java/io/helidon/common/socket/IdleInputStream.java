/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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
import java.net.Socket;
import java.net.SocketTimeoutException;
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

    /**
     * This needs to cooperate with 100-continue timeout.
     * Some low number is needed here for a quick iterations.
     * Using these small iterations allows us to cancel the idle check much faster,
     * without the need to wait for a full socket read timeout.
     */
    private static final int ITERATION_TIME_MILLIS = 101;

    private final Socket socket;
    private final InputStream upstream;
    private final LazyValue<ExecutorService> executor;
    private volatile int next = -1;
    private volatile boolean closed = false;
    private volatile boolean cancelled = false;
    private Future<?> idlingThread;

    IdleInputStream(Socket socket, InputStream upstream, String childSocketId, String socketId) {
        this.socket = socket;
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
            //Currently configured socket read timeout. This is intended to be restored after this method finishes.
            int toRestore = socket.getSoTimeout();
            int idleTimeoutIterations = Math.ceilDiv(Math.max(1, toRestore), ITERATION_TIME_MILLIS);
            for (int i = 0; !cancelled; i++) {
                try {
                    //We need to check the current socket timeout before each iteration.
                    //This time out could have changed,
                    //and now it would represent the new timeout we should restore after this method ends.
                    int currentSoTimeout = socket.getSoTimeout();
                    if (currentSoTimeout != ITERATION_TIME_MILLIS) {
                        toRestore = currentSoTimeout;
                    }
                    //Set iteration read timeout
                    socket.setSoTimeout(ITERATION_TIME_MILLIS);
                    next = upstream.read();
                    if (next <= 0) {
                        closed = true;
                        return;
                    }
                    break;
                } catch (SocketTimeoutException e) {
                    if (i + 1 >= idleTimeoutIterations) {
                        throw e;
                    }
                }
            }
            //Idle checking thread was canceled or detected as not idle. Restore socket timeout it should have.
            socket.setSoTimeout(toRestore);
        } catch (IOException e) {
            closed = true;
            throw new UncheckedIOException(e);
        }
    }

    private void endIdle() {
        try {
            cancelled = true;
            idlingThread.get();
            idlingThread = null;
            cancelled = false;
        } catch (InterruptedException | ExecutionException e) {
            closed = true;
            throw new RuntimeException("Exception in socket monitor thread.", e);
        }
    }
}
