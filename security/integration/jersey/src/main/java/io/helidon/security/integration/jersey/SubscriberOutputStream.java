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
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

/**
 * An {@link Flow.Subscriber subscriber} that can subscribe to a source of {@code ByteBuffer} data chunks and then
 * write these data chunks into an underlying {@link OutputStream output stream}.
 */
@SuppressWarnings("WeakerAccess")
class SubscriberOutputStream implements Flow.Subscriber<ByteBuffer> {

    private final OutputStream outputStream;
    private final Consumer<Throwable> completionCallback;

    private volatile Flow.Subscription subscription;

    /**
     * Create new subscriber that writes all the receive data chunks serially into a supplied output stream.
     *
     * @param outputStream       underlying output stream to send the data to.
     * @param completionCallback a callback to be invoked when the {@link #onComplete()} method is invoked and the underlying
     *                           output stream has been closed. The callback accepts an error, that may be either {@code null}
     *                           in case of a successful completion, or may contain information about any failure related to
     *                           closing the underlying output stream.
     */
    SubscriberOutputStream(OutputStream outputStream, Consumer<Throwable> completionCallback) {
        this.outputStream = outputStream;
        this.completionCallback = completionCallback;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1);
    }

    @Override
    public void onNext(ByteBuffer dataBuffer) {
        try {
            if (dataBuffer.hasArray()) {
                outputStream.write(
                        dataBuffer.array(), dataBuffer.arrayOffset() + dataBuffer.position(), dataBuffer.remaining());
            } else {
                byte[] data = new byte[dataBuffer.remaining()];
                outputStream.write(data);
            }
            subscription.request(1);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        // ignored
    }

    @Override
    public void onComplete() {
        try {
            outputStream.close();
            completionCallback.accept(null);
        } catch (IOException e) {
            completionCallback.accept(e);
        }
    }
}
