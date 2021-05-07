/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.integration.jersey;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;

/**
 * An {@link Flow.Subscriber subscriber} that can subscribe to a source of {@code ByteBuffer} data chunks and then make
 * them available for consumption via standard blocking {@link InputStream} API.
 */
class SubscriberInputStream extends InputStream implements Flow.Subscriber<ByteBuffer> {

    private volatile Flow.Subscription subscription;

    // Operations that depend on both the future and the closed flag below must be handled under a lock to avoid race
    // conditions where they may interleave, e.g.
    //
    //  1. read() thread sees closed == false
    //  2. onComplete() thread changes closed to true
    //  3. onComplete() calls processed.complete(null)
    //  4. read() thread replaces processed with new instance
    //  5. read() thread loops and blocks forever on processed.get() since the new instance in step 4 will never complete

    private volatile CompletableFuture<ByteBuffer> processed = new CompletableFuture<>();
    private boolean closed = false;

    @Override
    public int read() throws IOException {
        try {
            while (true) {
                final ByteBuffer currentBuffer = processed.get(); // block until we have a buffer
                if (currentBuffer != null) {
                    if (currentBuffer.remaining() > 0) {
                        // There's at least one more byte, return it
                        return currentBuffer.get();
                    } else {
                        // We've finished with the current buffer; if we're still open, reinitialize the future
                        // and request more data
                        if (reinitializeFuture()) {
                            subscription.request(1);
                        } else {
                            // We're closed
                            return -1;
                        }
                    }
                } else {
                    // We're closed
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
        if (item != null) {
            processed.complete(item);
        }
    }

    @Override
    public synchronized void onError(Throwable throwable) {
        closed = true;
        if (!processed.completeExceptionally(throwable)) { // best effort exception propagation
            final CompletableFuture<ByteBuffer> cf = new CompletableFuture<>();
            cf.completeExceptionally(throwable);
            processed = cf;
        }
    }

    @Override
    public synchronized void onComplete() {
        closed = true;
        processed.complete(null); // complete if not already done
    }

    private synchronized boolean reinitializeFuture() {
        final boolean open = !closed;
        if (open) {
            processed = new CompletableFuture<>();
        }
        return open;
    }
}
