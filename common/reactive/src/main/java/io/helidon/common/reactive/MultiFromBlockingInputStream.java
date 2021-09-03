/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;

class MultiFromBlockingInputStream extends MultiFromInputStream {

    private final InputStream inputStream;
    private IntSupplier bufferSizeSupplier;
    private final ExecutorService executorService;

    MultiFromBlockingInputStream(InputStream inputStream, int bufferSize, ExecutorService executorService) {
        super(inputStream, bufferSize);
        this.inputStream = inputStream;
        this.bufferSizeSupplier = () -> bufferSize;
        this.executorService = executorService;
    }

    @Override
    public void subscribe(final Flow.Subscriber<? super ByteBuffer> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber is null");
        try {
            inputStream.available();
        } catch (IOException e) {
            subscriber.onSubscribe(EmptySubscription.INSTANCE);
            subscriber.onError(e);
            return;
        }
        InputStreamSubscription subscription = new InputStreamSubscription(
                subscriber,
                inputStream,
                bufferSizeSupplier.getAsInt(),
                executorService);
        DeferredSubscription ds = new DeferredSubscription();
        subscriber.onSubscribe(ds);
        ds.setSubscription(subscription);
    }

    @Override
    public Multi<ByteBuffer> withByteBufferSize(final int bufferSize) {
        this.bufferSizeSupplier = () -> bufferSize;
        return this;
    }

    static final class InputStreamSubscription extends MultiFromInputStream.InputStreamSubscription {

        private final ExecutorService executorService;
        private final LinkedBlockingQueue<Runnable> submitQueue = new LinkedBlockingQueue<>();

        private final AtomicBoolean draining = new AtomicBoolean(false);

        InputStreamSubscription(Flow.Subscriber<? super ByteBuffer> downstream,
                                InputStream inputStream,
                                final int bufferSize,
                                ExecutorService executorService) {
            super(downstream, inputStream, bufferSize);
            this.executorService = executorService;
        }

        protected void trySubmit(long n) {
            submitQueue.add(() -> {
                submit(n);
                drainSubmitQueue();
            });
            drainSubmitQueue();
        }

        private void drainSubmitQueue() {
            if (!draining.getAndSet(true)) {
                try {
                    Runnable job = submitQueue.poll();
                    if (job != null) {
                        executorService.submit(job);
                    }
                } finally {
                    draining.set(false);
                }
            }
        }
    }
}
