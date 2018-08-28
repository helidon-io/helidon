/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.jersey;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.reactive.Flow;

/**
 * An {@link Flow.Subscriber subscriber} that can subscribe to a source of {@code ByteBuffer} data chunks and then make
 * them available for consumption via standard blocking {@link InputStream} API.
 */
public class SubscriberInputStream extends InputStream implements Flow.Subscriber<ByteBuffer> {

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile Flow.Subscription subscription;
    private volatile CompletableFuture<ByteBuffer> processed = new CompletableFuture<>();

    @Override
    public int read() throws IOException {
        try {
            while (true) {
                ByteBuffer currentBuffer = processed.get(); // block until a processing data are available

                if (currentBuffer != null && currentBuffer.remaining() > 0) {
                    // if there is anything to read, then read one byte...
                    return currentBuffer.get();
                } else if (!closed.get()) {
                    // reinitialize the processed buffer future and request more data
                    processed = new CompletableFuture<>();
                    subscription.request(1);
                } else {
                    // else we have read all the data already and the data inflow has completed
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
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1);
    }

    @Override
    public void onNext(ByteBuffer item) {
        processed.complete(item);
    }

    @Override
    public void onError(Throwable throwable) {
        closed.set(true);
        if (!processed.completeExceptionally(throwable)) { // best effort exception propagation
            CompletableFuture<ByteBuffer> cf = new CompletableFuture<>();
            cf.completeExceptionally(throwable);
            processed = cf;
        }
    }

    @Override
    public void onComplete() {
        closed.set(true);
        processed.complete(null); // if not already completed, then complete
    }

}
