/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.reactive.Flow;
import io.helidon.common.reactive.RequestedCounter;
import io.helidon.common.reactive.SingleSubscriberHolder;

/**
 * Publisher that reads data from an input stream and publishes them as {@link java.nio.ByteBuffer} events.
 *
 * @deprecated Until new reactive library is merged, we need to copy this class here
 */
@SuppressWarnings("Duplicates")
@Deprecated
class InputStreamPublisher implements Flow.Publisher<ByteBuffer> {
    private final InputStream inputStream;
    private final byte[] buffer;

    private final SingleSubscriberHolder<ByteBuffer> subscriber = new SingleSubscriberHolder<>();

    private final RequestedCounter requested = new RequestedCounter();
    private final AtomicBoolean publishing = new AtomicBoolean(false);

    /**
     * Create new input stream publisher that reads data from a supplied input stream and publishes them a single subscriber.
     * <p>
     * Note that this implementation does not rely on any asynchronous processing and its business logic is always invoked
     * on the subscriber thread (as part of {@link #subscribe(io.helidon.common.reactive.Flow.Subscriber)} and
     * {@link io.helidon.common.reactive.Flow.Subscription#request(long)}
     * method calls).
     *
     * @param inputStream underlying input stream to be used to read the data tu be published as events.
     * @param bufferSize  maximum published event data buffer size.
     */
    InputStreamPublisher(InputStream inputStream, int bufferSize) {
        this.inputStream = inputStream;
        this.buffer = new byte[bufferSize];
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriberParam) {
        if (subscriber.register(subscriberParam)) {
            publishing.set(true); // prevent onNext from inside of onSubscribe

            try {
                subscriberParam.onSubscribe(new Flow.Subscription() {
                    @Override
                    public void request(long n) {
                        requested.increment(n, t -> tryComplete(t));
                        tryPublish();
                    }

                    @Override
                    public void cancel() {
                    }
                });
            } finally {
                publishing.set(false);
            }

            tryPublish(); // give onNext a chance in case request has been invoked in onSubscribe
        }
    }

    private void tryPublish() {
        while (!subscriber.isClosed() && (requested.get() > 0) && publishing.compareAndSet(false, true)) {
            try {
                final Flow.Subscriber<? super ByteBuffer> sub = this.subscriber.get(); // blocking retrieval

                while (!subscriber.isClosed() && requested.tryDecrement()) {
                    int len = inputStream.read(buffer);
                    if (len >= 0) {
                        sub.onNext(ByteBuffer.wrap(buffer, 0, len));
                    } else {
                        inputStream.close();
                        tryComplete();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                tryComplete(e);
            } catch (IOException | ExecutionException e) {
                tryComplete(e);
            } finally {
                publishing.set(false); // give a chance to some other thread to publish
            }
        }
    }

    private void tryComplete() {
        subscriber.close(Flow.Subscriber::onComplete);
    }

    private void tryComplete(Throwable t) {
        subscriber.close(sub -> sub.onError(t));
    }
}
