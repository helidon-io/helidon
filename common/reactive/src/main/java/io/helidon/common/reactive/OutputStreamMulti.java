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
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Output stream that {@link java.util.concurrent.Flow.Publisher} publishes any data written to it as {@link ByteBuffer}
 * events.
 */
@SuppressWarnings("WeakerAccess")
public class OutputStreamMulti extends OutputStream implements Multi<ByteBuffer> {

    private long timeout = Duration.ofMinutes(10).toMillis();

    private static final byte[] FLUSH_BUFFER = new byte[0];

    private final EmittingPublisher<ByteBuffer> emittingPublisher = EmittingPublisher.create();
    private final CompletableFuture<?> completionResult = new CompletableFuture<>();
    private final AtomicBoolean written = new AtomicBoolean();
    private volatile CompletableFuture<Void> demandUpdated = new CompletableFuture<>();

    /**
     * Create new output stream that {@link java.util.concurrent.Flow.Publisher}
     * publishes any data written to it as {@link ByteBuffer} events.
     */
    protected OutputStreamMulti() {
        emittingPublisher.onCancel(() -> {
            // when write is called, an exception is thrown as it is a cancelled subscriber
            // when close is called, we do not throw an exception, as that should be silent
            completionResult.complete(null);
        });
        emittingPublisher.onRequest((n, demand) -> {
            // complete previous and create new future for demand update
            CompletableFuture<Void> demandUpdated = this.demandUpdated;
            this.demandUpdated = new CompletableFuture<>();
            demandUpdated.complete(null);
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
    public OutputStreamMulti onRequest(BiConsumer<Long, Long> requestCallback){
        this.emittingPublisher.onRequest(requestCallback);
        return this;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        if (completionResult.isCompletedExceptionally()) {
            subscriber.onSubscribe(SubscriptionHelper.CANCELED);
            completionResult.whenComplete((o, throwable) -> subscriber.onError(throwable));
            return;
        }
        emittingPublisher.subscribe(subscriber);
    }

    @Override
    public void write(byte[] b) throws IOException {
        publish(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        publish(b, off, len);
    }

    @Override
    public void write(int b) throws IOException {
        byte bb = (byte) b;
        publish(new byte[] {bb}, 0, 1);
    }

    @Override
    public void close() throws IOException {
        complete();

        if (!written.get() && !completionResult.isCompletedExceptionally()) {
            // no need to wait for
            return;
        }
        try {
            completionResult.get(timeout, TimeUnit.MILLISECONDS);
        } catch (CancellationException e) {
            throw new IOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        } catch (ExecutionException e) {
            throw new IOException(e.getCause());
        } catch (TimeoutException e) {
            throw new IOException("Timed out while waiting for subscriber to read data", e);
        }
    }

    /**
     * Signals this publishing output stream that it can safely return from otherwise blocking invocation
     * to it's {@link #close()} method.
     * Subsequent multiple invocations of this method are allowed, but have no effect on this publishing output stream.
     * <p>
     * When the {@link #close()} method on this output stream is invoked, it will block waiting for a signal to complete.
     * This is useful in cases, when the receiving side needs to synchronize it's completion with the publisher, e.g. to
     * ensure that any resources used by the subscribing party are not released prematurely due to a premature exit from
     * publishing output stream {@code close()} method.
     * <p>
     * Additionally, this mechanism can be used to propagate any downstream completion exceptions back to this publisher
     * and up it's call stack. When a non-null {@code throwable} parameter is passed into the method, it will be wrapped
     * in an {@link IOException} and thrown from the {@code close()} method when it is invoked.
     *
     * @param throwable represents a completion error condition that should be thrown when a {@code close()} method is invoked
     *                  on this publishing output stream. If set to {@code null}, the {@code close()} method will exit normally.
     */
    void signalCloseComplete(Throwable throwable) {
        if (throwable == null) {
            completionResult.complete(null);
        } else {
            completionResult.completeExceptionally(throwable);
        }
    }

    /**
     * Send empty buffer as an indication of a user-requested flush.
     *
     * @throws IOException If an I/O occurs.
     */
    @Override
    public void flush() throws IOException {
        publish(FLUSH_BUFFER, 0, 0);
    }

    private void publish(byte[] buffer, int offset, int length) throws IOException {
        Objects.requireNonNull(buffer);

        if (length > 0) {
            written.set(true);
        }

        try {
            long start = System.currentTimeMillis();

            ByteBuffer byteBuffer = createBuffer(buffer, offset, length);

            // defend against racing demand updates
            CompletableFuture<Void> demandUpdated = this.demandUpdated;
            while (!emittingPublisher.emit(byteBuffer)) {
                if (emittingPublisher.isCancelled()) {
                    throw new IOException("Output stream already closed.");
                }
                if (emittingPublisher.isFailed()) {
                    Throwable throwable = emittingPublisher.failCause().get();
                    throw new IOException(throwable);
                }

                // wait until some data can be sent or the stream has been closed
                await(start, 250, demandUpdated);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            complete(e);
            throw new IOException(e);
        } catch (ExecutionException e) {
            complete(e.getCause());
            throw new IOException(e.getCause());
        } catch (IllegalStateException e) {
            complete(e);
            throw new IOException(e);
        }
    }

    private void complete() {
        emittingPublisher.complete();
        signalCloseComplete(null);
    }

    private void complete(Throwable t) {
        emittingPublisher.fail(t);
        signalCloseComplete(t);
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

    /**
     * Creates a {@link ByteBuffer} by making a copy of the underlying
     * byte array. Jersey will reuse this array, so it needs to be
     * copied here.
     *
     * @param buffer The buffer.
     * @param offset Offset in buffer.
     * @param length Length of buffer.
     * @return Newly created {@link ByteBuffer}.
     */
    private ByteBuffer createBuffer(byte[] buffer, int offset, int length) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(length - offset);
        byteBuffer.put(buffer, offset, length);
        return byteBuffer.clear();     // resets counters
    }
}
