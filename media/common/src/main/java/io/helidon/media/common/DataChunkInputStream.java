/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.media.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import io.helidon.common.http.DataChunk;

/**
 * Provides a bridge between a reactive {@link Flow.Publisher} in Helidon and an {@link InputStream}
 * in Jersey. It subscribes to a Helidon publisher of data chunks and makes the data available to
 * Jersey using the blocking {@link InputStream} API.
 *
 * This implementation is documented here {@code /docs-internal/datachunkinputstream.md}.
 */
public class DataChunkInputStream extends InputStream {
    private static final Logger LOGGER = Logger.getLogger(DataChunkInputStream.class.getName());

    private final String originalThreadID;
    private final boolean validate;
    private final Flow.Publisher<DataChunk> originalPublisher;
    private int bufferIndex;
    private CompletableFuture<DataChunk> current = new CompletableFuture<>();
    private CompletableFuture<DataChunk> next = current;
    private volatile Flow.Subscription subscription;
    private byte[] oneByte;

    /**
     * This really doesn't need to be AtomicBoolean - all accesses are not thread-safe anyway, so
     * are meant to be single-threaded. This remains AtomicBoolean just in case there still is some
     * use-case where the existence of the full memory fence on compareAndSet introduces the
     * "out-of-bands synchronization" necessary for total ordering of read(...) and close(...).
     */
    private final AtomicBoolean subscribed = new AtomicBoolean(false);

    /**
     * Stores publisher for later subscription. Disables executing thread validation.
     *
     * @param originalPublisher The original publisher.
     */
    public DataChunkInputStream(Flow.Publisher<DataChunk> originalPublisher) {
        this(originalPublisher, false);
    }

    /**
     * Stores publisher for later subscription and sets if executing thread should be validated.
     * If validation is enabled, it asserts if the execution thread is the same as the thread which created this instance and
     * throws an {@link IllegalStateException} if it does.
     *
     * @param originalPublisher The original publisher.
     * @param validate          executing thread validation
     */
    public DataChunkInputStream(Flow.Publisher<DataChunk> originalPublisher, boolean validate) {
        this.originalPublisher = originalPublisher;
        this.originalThreadID = getCurrentThreadIdent();
        this.validate = validate;
    }

    /**
     * Releases a data chunk.
     *
     * @param chunk The chunk.
     * @param th A throwable.
     */
    private static void releaseChunk(DataChunk chunk, Throwable th) {
        if (chunk != null && !chunk.isReleased()) {
            LOGGER.finest(() -> "Releasing chunk: " + chunk.id());
            chunk.release();
        }
    }

    // -- InputStream ---------------------------------------------------------
    //
    // Following methods are executed by Jersey/Helidon threads
    // ------------------------------------------------------------------------

    @Override
    public void close() {
        // Assert: if current != next, next cannot ever be resolved with a chunk that needs releasing
        Optional.ofNullable(current).ifPresent(it -> current.whenComplete(DataChunkInputStream::releaseChunk));
        current = null;
        bufferIndex = 0;
    }

    @Override
    public int read() throws IOException {
        if (oneByte == null) {
            oneByte = new byte[1];
        }
        // Assert: Chunks are always non-empty, so r is either 1 or negative (EOF)
        int r = read(oneByte, 0, 1);
        if (r < 0) {
            return r;
        }

        return oneByte[0] & 0xFF;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        validate();
        if (subscribed.compareAndSet(false, true)) {
            originalPublisher.subscribe(new DataChunkSubscriber());       // subscribe for first time
        }

        if (current == null) {
            throw new IOException("The input stream has been closed");
        }

        try {
            DataChunk chunk = current.get();    // block until data is available
            if (chunk == null) {
                return -1;
            }

            ByteBuffer[] currentBuffers = chunk.data();
            int count = 0;
            for (int i = bufferIndex; i < currentBuffers.length; i++) {

                if (i == 0 && currentBuffers[i].position() == 0) {
                    LOGGER.finest(() -> "Reading chunk ID: " + chunk.id());
                }

                int rem = currentBuffers[i].remaining();
                int blen = len;
                if (blen > rem) {
                    blen = rem;
                }
                currentBuffers[i].get(buf, off, blen);
                off += blen;
                count += blen;
                len -= blen;

                if (rem > blen) {
                    break;
                }
                if (count < len && i < currentBuffers.length - 1) {
                    bufferIndex++;
                }

                // Chunk is consumed entirely - release the chunk, and prefetch a new chunk; do not
                // wait for it to arrive - the next read may have to wait less.
                //
                // Assert: it is safe to request new chunks eagerly - there is no mechanism
                // to push back unconsumed data, so we can assume we own all the chunks,
                // consumed and unconsumed.
                if (i == currentBuffers.length - 1 && blen == rem) {
                    releaseChunk(chunk, null);
                    current = next;
                    bufferIndex = 0;
                    subscription.request(1);
                }
            }
            return count;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        } catch (ExecutionException e) {
            throw new IOException(e.getCause());
        }
    }

    private String getCurrentThreadIdent() {
        Thread thread = Thread.currentThread();
        return thread.getName() + ":" + thread.getId();
    }

    private void validate() {
        if (validate && originalThreadID.equals(getCurrentThreadIdent())) {
            throw new IllegalStateException("DataChunkInputStream needs to be handled in separate thread to prevent deadlock.");
        }
    }

    // -- DataChunkSubscriber -------------------------------------------------
    //
    // Following methods are executed by Netty IO threads (except first chunk)
    // ------------------------------------------------------------------------

    private class DataChunkSubscriber implements Flow.Subscriber<DataChunk> {

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            DataChunkInputStream.this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(DataChunk item) {
            LOGGER.finest(() -> "Processing chunk: " + item.id());
            if (item.remaining() > 0) {
                CompletableFuture<DataChunk> prev = next;
                next = new CompletableFuture<>();
                prev.complete(item);
            } else {
                releaseChunk(item, null);
                subscription.request(1);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            next.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            next.complete(null);
        }
    }
}
