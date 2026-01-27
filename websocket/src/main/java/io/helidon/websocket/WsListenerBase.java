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

package io.helidon.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.Functions;
import io.helidon.common.buffers.BufferData;

/**
 * A base class for WebSocket listeners to support readers and input streams.
 * <p>
 * <strong>IMPORTANT:</strong> use this class only for listeners that are registered as suppliers (i.e. you get a new
 * instance for each web socket session), otherwise the thread guarantees will be broken and this will not work.
 */
public abstract class WsListenerBase implements WsListener {
    /*
    This class is used from generated code for Helidon Declarative
     */

    private static final System.Logger LOGGER = System.getLogger(WsListenerBase.class.getName());
    private static final ThreadFactory THREAD_FACTORY = Thread.ofVirtual()
            .name("ws-listener-handler-", 0L)
            .uncaughtExceptionHandler((t, e) -> {
                LOGGER.log(Level.TRACE,
                           "Uncaught exception while handling asynchronous websocket operation", e);
            })
            .factory();
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newThreadPerTaskExecutor(THREAD_FACTORY);

    // this does not need to be guarded, as we always use it from the connection thread
    private final StringBuilder stringBuilder = new StringBuilder();

    private final AtomicReference<Future<?>> binaryFuture = new AtomicReference<>();
    private final AtomicReference<Future<?>> textFuture = new AtomicReference<>();

    // this also does not need to be guarded, as the field is only accessed from the connection thread
    private SynchronousQueue<BinaryPayload> currentStreamQueue;
    private SynchronousQueue<TextPayload> currentReaderQueue;

    private List<BufferData> buffers;

    protected void textString(WsSession session,
                              String text,
                              boolean last,
                              Functions.CheckedConsumer<String, ?> stringConsumer) {
        stringBuilder.append(text);

        if (last) {
            try {
                stringConsumer.accept(stringBuilder.toString());
            } catch (Throwable e) {
                onError(session, e);
            }
            stringBuilder.setLength(0);
        }
    }

    // runs on current connection thread
    protected void textReader(WsSession session,
                              String text,
                              boolean last,
                              Functions.CheckedConsumer<Reader, ?> readerConsumer) {
        boolean newReader = currentReaderQueue == null;
        if (newReader) {
            currentReaderQueue = new SynchronousQueue<>();
        }
        var finalQueue = currentReaderQueue;

        if (newReader) {
            textFuture.set(EXECUTOR_SERVICE.submit(() -> {
                try {
                    readerConsumer.accept(new WsReader(finalQueue));
                } catch (Throwable e) {
                    LOGGER.log(Level.TRACE,
                               "Uncaught exception while handling asynchronous reader operation",
                               e);
                }
            }));
        }

        try {
            // the other side MUST read the data - otherwise we may do operations that are not consistent
            // such as closing the socket before the stream is processed
            finalQueue.put(new TextPayload(text, last));
        } catch (InterruptedException e) {
            if (LOGGER.isLoggable(Level.TRACE)) {
                LOGGER.log(Level.TRACE, "Text payload handling interrupted", e);
            }
            throw new WsCloseException("Text payload handling interrupted", WsCloseCodes.UNEXPECTED_CONDITION);
        }

        if (last) {
            currentReaderQueue = null;
            try {
                // we have delivered the last chunk of data, now we must wait for the async work
                // to complete, before doing anything else on this listener
                // this is to guarantee that a send and close from client is delivered sequentially, rather than in parallel
                textFuture.get().get();
            } catch (InterruptedException e) {
                if (LOGGER.isLoggable(Level.TRACE)) {
                    LOGGER.log(Level.TRACE, "Text onMessage handling interrupted", e);
                }
                throw new WsCloseException("Text onMessage handling interrupted", WsCloseCodes.UNEXPECTED_CONDITION);
            } catch (ExecutionException e) {
                if (LOGGER.isLoggable(Level.TRACE)) {
                    LOGGER.log(Level.TRACE, "Text onMessage asynchronous execution failed", e);
                }
                onError(session, e.getCause());
            } finally {
                textFuture.set(null);
            }
        }
    }

    protected void binaryBufferData(WsSession session,
                                    BufferData buffer,
                                    boolean last,
                                    Functions.CheckedConsumer<BufferData, ?> bufferDataConsumer) {

        if (buffers == null) {
            buffers = new ArrayList<>();
        }
        buffers.add(buffer);
        if (last) {
            try {
                bufferDataConsumer.accept(BufferData.create(buffers));
            } catch (Throwable e) {
                onError(session, e);
            }
            buffers = null;
        }
    }

    protected void binaryByteBuffer(WsSession session,
                                    BufferData buffer,
                                    boolean last,
                                    Functions.CheckedConsumer<ByteBuffer, ?> streamConsumer) {

        if (buffers == null) {
            buffers = new ArrayList<>();
        }
        buffers.add(buffer);
        if (last) {
            try {
                BufferData combined = BufferData.create(buffers);
                ByteBuffer byteBuffer = ByteBuffer.allocate(combined.available());
                combined.writeTo(byteBuffer, combined.available());
                byteBuffer.flip();
                streamConsumer.accept(byteBuffer);
            } catch (Throwable e) {
                onError(session, e);
            }
            buffers = null;
        }
    }

    protected void binaryByteArray(WsSession session,
                                   BufferData buffer,
                                   boolean last,
                                   Functions.CheckedConsumer<byte[], ?> byteArrayConsumer) {

        if (buffers == null) {
            buffers = new ArrayList<>();
        }
        buffers.add(buffer);
        if (last) {
            try {
                BufferData combined = BufferData.create(buffers);
                byte[] byteArray = new byte[combined.available()];
                combined.read(byteArray);
                byteArrayConsumer.accept(byteArray);
            } catch (Throwable e) {
                onError(session, e);
            }
            buffers = null;
        }
    }

    protected void binaryInputStream(WsSession session,
                                     BufferData buffer,
                                     boolean last,
                                     Functions.CheckedConsumer<InputStream, ?> streamConsumer) {
        boolean newStream = currentStreamQueue == null;
        if (newStream) {
            currentStreamQueue = new SynchronousQueue<>();
        }
        var finalQueue = currentStreamQueue;

        if (newStream) {
            binaryFuture.set(EXECUTOR_SERVICE.submit(() -> {
                try {
                    streamConsumer.accept(new WsInputStream(finalQueue));
                } catch (Throwable e) {
                    LOGGER.log(Level.TRACE,
                               "Uncaught exception while handling asynchronous stream operation",
                               e);
                }
            }));
        }

        try {
            // the other side MUST read the data - otherwise we may do operations that are not consistent
            // such as closing the socket before the stream is processed
            finalQueue.put(new BinaryPayload(buffer, last));
        } catch (InterruptedException e) {
            if (LOGGER.isLoggable(Level.TRACE)) {
                LOGGER.log(Level.TRACE, "Text payload handling interrupted", e);
            }
            throw new WsCloseException("Text payload handling interrupted", WsCloseCodes.UNEXPECTED_CONDITION);
        }

        if (last) {
            currentStreamQueue = null;
            try {
                // we have delivered the last chunk of data, now we must wait for the async work
                // to complete, before doing anything else on this listener
                // this is to guarantee that a send and close from client is delivered sequentially, rather than in parallel
                binaryFuture.get().get();
            } catch (InterruptedException e) {
                if (LOGGER.isLoggable(Level.TRACE)) {
                    LOGGER.log(Level.TRACE, "Binary onMessage handling interrupted", e);
                }
                throw new WsCloseException("Binary onMessage thread interrupted", WsCloseCodes.UNEXPECTED_CONDITION);
            } catch (ExecutionException e) {
                if (LOGGER.isLoggable(Level.TRACE)) {
                    LOGGER.log(Level.TRACE, "Binary onMessage asynchronous execution failed", e);
                }
                onError(session, e.getCause());
            } finally {
                binaryFuture.set(null);
            }
        }
    }

    private static class WsInputStream extends InputStream {
        private final BlockingQueue<BinaryPayload> queue;
        private volatile BinaryPayload current;

        private WsInputStream(BlockingQueue<BinaryPayload> queue) {
            this.queue = queue;
        }

        @Override
        public int read() throws IOException {
            if (!ensureAvailable()) {
                return -1;
            }

            return current.data.read();
        }

        @Override
        public int read(byte[] b) throws IOException {
            if (!ensureAvailable()) {
                return -1;
            }

            return current.data.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (!ensureAvailable()) {
                return -1;
            }
            return current.data.read(b, off, len);
        }

        private boolean ensureAvailable() throws IOException {
            if (current == null || current.data().consumed()) {
                try {
                    if (current != null && current.last()) {
                        return false;
                    }
                    current = queue.take();
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
            }
            return true;
        }
    }

    private static class WsReader extends Reader {
        private final BlockingQueue<TextPayload> queue;
        private boolean last = false;
        private int next = 0;
        private int len = 0;
        private String current;

        private WsReader(BlockingQueue<TextPayload> queue) {
            this.queue = queue;
        }

        @Override
        public int read() throws IOException {
            if (!ensureAvailable()) {
                return -1;
            }
            return current.charAt(next++);
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            if (!ensureAvailable()) {
                return -1;
            }
            int toRead = Math.min(len, this.len);
            System.arraycopy(this.current.toCharArray(), next, cbuf, off, toRead);
            next += toRead;
            return toRead;
        }

        @Override
        public boolean ready() {
            return next < len;
        }

        @Override
        public void close() {
        }

        private boolean ensureAvailable() throws IOException {
            if (last && next >= len) {
                return false;
            }
            if (current == null || next >= len) {
                try {
                    len = 0;
                    while (len == 0 && !last) {
                        var taken = queue.take();
                        last = taken.last();
                        current = taken.text();
                        len = current.length();
                        next = 0;
                    }
                    if (len == 0) {
                        return false;
                    }
                    return true;
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
            }
            return true;
        }
    }

    private record TextPayload(String text, boolean last) {
    }

    private record BinaryPayload(BufferData data, boolean last) {
    }

}
