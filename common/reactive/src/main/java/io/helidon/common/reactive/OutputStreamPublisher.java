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

package io.helidon.common.reactive;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 * Output stream that {@link io.helidon.common.reactive.Flow.Publisher publishes} any data written to it as {@link ByteBuffer}
 * events.
 */
@SuppressWarnings("WeakerAccess")
public class OutputStreamPublisher extends OutputStream implements Flow.Publisher<ByteBuffer> {

    private final SingleSubscriberHolder<ByteBuffer> subscriber = new SingleSubscriberHolder<>();
    private final Object invocationLock = new Object();

    private final RequestedCounter requested = new RequestedCounter();

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriberParam) {
        if (subscriber.register(subscriberParam)) {
            subscriberParam.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    requested.increment(n, t -> complete(t));
                }

                @Override
                public void cancel() {
                    subscriber.cancel();
                }
            });
        }
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
    }

    private void publish(byte[] buffer, int offset, int length) throws IOException {
        Objects.requireNonNull(buffer);

        try {
            final Flow.Subscriber<? super ByteBuffer> sub = subscriber.get();

            while (!subscriber.isClosed() && !requested.tryDecrement()) {
                Thread.sleep(250); // wait until some data can be sent or the stream has been closed
            }

            synchronized (invocationLock) {
                if (subscriber.isClosed()) {
                    throw new IOException("Output stream already closed.");
                }

                sub.onNext(ByteBuffer.wrap(buffer, offset, length));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            complete(e);
            throw new IOException(e);
        } catch (ExecutionException e) {
            complete(e.getCause());
            throw new IOException(e.getCause());
        }
    }

    private void complete() {
        subscriber.close(sub -> {
            synchronized (invocationLock) {
                sub.onComplete();
            }
        });
    }

    private void complete(Throwable t) {
        subscriber.close(sub -> {
            synchronized (invocationLock) {
                sub.onError(t);
            }
        });
    }
}
