/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Flow;

/**
 * An {@link Flow.Subscriber subscriber} that can subscribe to a source of {@code ByteBuffer} data chunks and then make
 * them available for consumption via standard blocking {@link InputStream} API.
 */
public class PublisherInputStream extends InputStream implements Flow.Publisher<DataChunk> {

    private static final Logger LOGGER = Logger.getLogger(PublisherInputStream.class.getName());

    private final Flow.Publisher<DataChunk> originalPublisher;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile CompletableFuture<DataChunk> processed = new CompletableFuture<>();
    private volatile Flow.Subscription subscription;
    /**
     * Wraps the supplied publisher and adds a blocking {@link InputStream} based nature.
     * It is illegal to subscribe to the returned publisher.
     *
     * @param originalPublisher the original publisher to wrap
     */
    public PublisherInputStream(Flow.Publisher<DataChunk> originalPublisher) {
        this.originalPublisher = originalPublisher;
    }
    private final AtomicBoolean subscribed = new AtomicBoolean(false);

    private static void releaseChunk(DataChunk chunk) {
        if (chunk != null && !chunk.isReleased()) {
            LOGGER.finest(() -> "Releasing chunk: " + chunk.id());
            chunk.release();
        }
    }

    @Override
    public int read() throws IOException {

        if (subscribed.compareAndSet(false, true)) {
            // do the subscribe for the first time
            subscribe();
        }

        try {
            while (true) {

                DataChunk chunk = processed.get(); // block until a processing data are available
                ByteBuffer currentBuffer = chunk != null && !chunk.isReleased() ? chunk.data() : null;

                if (currentBuffer != null && currentBuffer.position() == 0) {
                    LOGGER.finest(() -> "Reading chunk ID: " + chunk.id());
                }

                if (currentBuffer != null && currentBuffer.remaining() > 0) {
                    // if there is anything to read, then read one byte...
                    return currentBuffer.get();
                } else if (!closed.get()) {
                    // reinitialize the processed buffer future and request more data
                    processed = new CompletableFuture<>();

                    releaseChunk(chunk);
                    subscription.request(1);
                } else {
                    LOGGER.finest(() -> "Ending stream: " + Optional.ofNullable(chunk).map(DataChunk::id).orElse(null));
                    // else we have read all the data already and the data inflow has completed
                    releaseChunk(chunk);
                    return -1;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        } catch (ExecutionException e) {
            throw new IOException(e.getCause());
        }
    }

    @Override
    public void subscribe(Flow.Subscriber<? super DataChunk> subscriber) {
        subscriber.onError(new UnsupportedOperationException("Subscribing on this publisher is not allowed!"));
    }

    private void subscribe() {
        originalPublisher.subscribe(new Flow.Subscriber<DataChunk>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                PublisherInputStream.this.subscription = subscription;
                subscription.request(1);
            }

            @Override
            public void onNext(DataChunk item) {
                LOGGER.finest(() -> "Processing chunk: " + item.id());
                processed.complete(item);
            }

            @Override
            public void onError(Throwable throwable) {
                closed.set(true);
                if (!processed.completeExceptionally(throwable)) {
                    // best effort exception propagation
                    processed = new CompletableFuture<>();
                    processed.completeExceptionally(throwable);
                }
            }

            @Override
            public void onComplete() {
                closed.set(true);
                processed.complete(null); // if not already completed, then complete
            }
        });
    }
}
