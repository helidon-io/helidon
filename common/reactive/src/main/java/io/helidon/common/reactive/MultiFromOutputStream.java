/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.common.reactive;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

/**
 * Output stream that {@link java.util.concurrent.Flow.Publisher} publishes any data written to it as {@link ByteBuffer}
 * events.
 *
 * @deprecated please use {@link io.helidon.common.reactive.OutputStreamMulti} instead
 */
@Deprecated(since = "2.0.0", forRemoval = true)
public class MultiFromOutputStream extends OutputStream implements Multi<ByteBuffer> {

    private static final int BUFFER_SIZE = 4 * 1024;
    private static final byte[] FLUSH_BUFFER = new byte[0];

    private long timeout = Duration.ofMinutes(10).toMillis();
    private final EmittingPublisher<ByteBuffer> emittingPublisher = EmittingPublisher.create();
    private volatile CompletableFuture<Void> demandUpdated = new CompletableFuture<>();
    private final ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);

    /**
     * Create new output stream that {@link java.util.concurrent.Flow.Publisher}
     * publishes any data written to it as {@link ByteBuffer} events.
     */
    protected MultiFromOutputStream() {
        emittingPublisher.onCancel(() -> {
            demandUpdated.cancel(true);
        });
        emittingPublisher.onRequest((n, demand) -> {
            this.demandUpdated.complete(null);
        });
    }

    void timeout(long timeout) {
        this.timeout = timeout;
    }

    /**
     * Callback executed when request signal from downstream arrive.
     * <ul>
     * <li><b>param</b> {@code n} the requested count.</li>
     * <li><b>param</b> {@code demand} the current total cumulative requested count,
     * ranges between [0, {@link Long#MAX_VALUE}] where the max indicates that this
     * publisher is unbounded.</li>
     * </ul>
     *
     * @param requestCallback to be executed
     * @return this OutputStreamMulti
     */
    public MultiFromOutputStream onRequest(BiConsumer<Long, Long> requestCallback) {
        this.emittingPublisher.onRequest(requestCallback);
        return this;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        emittingPublisher.subscribe(subscriber);
    }

    @Override
    public void write(byte[] b) throws IOException {
        publishBufferedMaybe();
        publish(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        publishBufferedMaybe();
        publish(b, off, len);
    }

    @Override
    public void write(int b) throws IOException {
        if (!byteBuffer.hasRemaining()) {
            publish();
        }
        byteBuffer.put((byte) b);      // just buffer, no publish
    }

    @Override
    public void close() throws IOException {
        publishBufferedMaybe();
        complete();
    }

    /**
     * Send empty buffer as an indication of a user-requested flush.
     *
     * @throws IOException If an I/O occurs.
     */
    @Override
    public void flush() throws IOException {
        publishBufferedMaybe();
        publish(FLUSH_BUFFER, 0, 0);
    }

    private void publishBufferedMaybe() throws IOException {
        if (byteBuffer.position() > 0) {
            publish();
        }
    }

    private void publish(byte[] b, int off, int len) throws IOException {
        ByteBuffer emitBuffer = ByteBuffer.allocate(len - off);
        emitBuffer.put(b, off, len);
        emitBuffer.flip();
        doPublish(emitBuffer);
    }

    private void publish() throws IOException {
        byteBuffer.flip();
        ByteBuffer emitBuffer = ByteBuffer.allocate(byteBuffer.remaining());
        emitBuffer.put(byteBuffer);
        emitBuffer.flip();
        doPublish(emitBuffer);
        byteBuffer.clear();
    }

    private void doPublish(ByteBuffer emitBuffer) throws IOException {
        try {
            long start = System.currentTimeMillis();

            while (!emittingPublisher.emit(emitBuffer)) {
                if (emittingPublisher.isCancelled()) {
                    throw new IOException("Output stream already closed.");
                }
                if (emittingPublisher.isFailed()) {
                    Throwable throwable = emittingPublisher.failCause().get();
                    throw new IOException(throwable);
                }

                // wait until some data can be sent or the stream has been closed
                await(start, timeout, demandUpdated);
                demandUpdated = new CompletableFuture<>();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail(e);
            throw new IOException(e);
        } catch (ExecutionException e) {
            fail(e.getCause());
            throw new IOException(e.getCause());
        } catch (IllegalStateException e) {
            fail(e);
            throw new IOException(e);
        }
    }

    void complete() {
        emittingPublisher.complete();
        demandUpdated.complete(null);
    }

    void fail(Throwable t) {
        emittingPublisher.fail(t);
        demandUpdated.completeExceptionally(t);
    }

    private void await(long startTime, long waitTime, CompletableFuture<?> future) throws
            ExecutionException,
            InterruptedException,
            IOException {
        try {
            future.get(waitTime, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            long diff = System.currentTimeMillis() - startTime;
            if (diff > timeout) {
                throw new IOException("Timed out while waiting for subscriber to read data");
            }
        }
    }
}
