/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;

class MultiFromInputStream implements MultiByteBuffer {

    private final InputStream inputStream;
    private IntSupplier bufferSizeSupplier;

    MultiFromInputStream(InputStream inputStream, int bufferSize) {
        this.inputStream = inputStream;
        this.bufferSizeSupplier = () -> bufferSize;
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
                bufferSizeSupplier.getAsInt());
        subscriber.onSubscribe(subscription);
    }

    public Multi<ByteBuffer> withByteBufferSize(final int bufferSize) {
        this.bufferSizeSupplier = () -> bufferSize;
        return this;
    }

    static class InputStreamSubscription extends AtomicLong implements Flow.Subscription {

        private final Flow.Subscriber<? super ByteBuffer> downstream;
        private final int bufferSize;
        private InputStream inputStream;

        private volatile int canceled;

        static final int NORMAL_CANCEL = 1;
        static final int BAD_REQUEST = 2;

        InputStreamSubscription(Flow.Subscriber<? super ByteBuffer> downstream,
                                InputStream inputStream,
                                final int bufferSize) {
            this.downstream = downstream;
            this.inputStream = inputStream;
            this.bufferSize = bufferSize;
        }

        protected void submit(long n) {
            long emitted = 0L;
            Flow.Subscriber<? super ByteBuffer> downstream = this.downstream;

            for (;;) {
                while (emitted != n) {
                    int isCanceled = canceled;
                    if (isCanceled != 0) {
                        inputStream = null;
                        if (isCanceled == BAD_REQUEST) {
                            downstream.onError(new IllegalArgumentException(
                                    "Rule §3.9 violated: non-positive request amount is forbidden"));
                        }
                        return;
                    }

                    ByteBuffer value;

                    try {
                        value = ByteBuffer.wrap(inputStream.readNBytes(bufferSize));
                    } catch (Throwable ex) {
                        inputStream = null;
                        canceled = NORMAL_CANCEL;
                        downstream.onError(ex);
                        return;
                    }

                    if (value.limit() == 0) {
                        inputStream = null;
                        downstream.onComplete();
                        return;
                    }

                    downstream.onNext(value);

                    if (canceled != 0) {
                        continue;
                    }

                    emitted++;
                }

                n = get();
                if (n == emitted) {
                    n = SubscriptionHelper.produced(this, emitted);
                    if (n == 0L) {
                        return;
                    }
                    emitted = 0L;
                }
            }
        }

        @Override
        public void request(long n) {
            if (n <= 0L) {
                canceled = BAD_REQUEST;
                n = 1; // for cleanup
            }

            if (SubscriptionHelper.addRequest(this, n) != 0L) {
                return;
            }

            trySubmit(n);
        }

        protected void trySubmit(long n) {
            submit(n);
        }

        @Override
        public void cancel() {
            canceled = NORMAL_CANCEL;
            request(1); // for cleanup
        }
    }
}
