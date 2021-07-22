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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * A subscriber container that accepts only a single, one-time subscriber registration.
 */
@SuppressWarnings("WeakerAccess")
class SingleSubscriberHolder<T> {
    private final CompletableFuture<Flow.Subscriber<? super T>> subscriber = new CompletableFuture<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Register a new subscriber.
     * <p>
     * In case the subscriber registration fails (e.g. the holder already holds a previously registered subscriber
     * or the holder has been {@link #close(Consumer) closed}), the newly registered subscriber is notified about the
     * error by invoking it's {@link Flow.Subscriber#onError(Throwable) subscriber.onError} method.
     *
     * @param subscriber subscriber to be registered in the holder.
     * @return {@code true} if the subscriber was successfully registered, {@code false} otherwise.
     */
    public boolean register(Flow.Subscriber<? super T> subscriber) {
        if (!this.subscriber.complete(subscriber)) {
            Throwable error = null;
            try {
                this.subscriber.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                error = e;
            } catch (ExecutionException e) {
                error = e.getCause();
            }

            subscriber.onError((error != null) ? error : new IllegalStateException(
                    "This publisher only supports a single subscriber."));
            return false;
        }

        return true;
    }

    /**
     * Mark the subscriber holder as closed.
     * <p>
     * Invoking this method will ensure that any new attempts to {@link #register(Flow.Subscriber) register} a new subscriber
     * would fail.
     * <p>
     * In case this holder holds a subscriber and the close method has not been invoked yet, the supplied
     * {@code completionHandler} is invoked using the value of the registered subscriber as an input parameter.
     * This means that the supplied completion handler is guaranteed to be invoked at most once.
     *
     * @param completionHandler completion handler to be invoked to process any completion logic on a registered subscriber,
     *                          provided there is a registered subscriber and it has not been previously passed to a completion
     *                          handler (e.g. in a previous invocation of this method).
     */
    public void close(Consumer<Flow.Subscriber<? super T>> completionHandler) {
        if (!subscriber.completeExceptionally(new IllegalStateException("Publisher already closed."))
                && closed.compareAndSet(false, true)) {

            try {
                final Flow.Subscriber<? super T> s = this.subscriber.get();
                completionHandler.accept(s);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                // ignore
            }
        }
    }

    /**
     * Get the stored subscriber.
     * <p>
     * This method blocks indefinitely until a subscriber is registered or the holder has been closed.
     *
     * @return registered subscriber.
     * @throws ExecutionException   if the subscriber retrieval has failed (e.g. because this holder has been closed
     *                              before a subscriber has been registered).
     * @throws InterruptedException if the current thread was interrupted
     */
    public Flow.Subscriber<? super T> get() throws InterruptedException, ExecutionException {
        return subscriber.get();
    }

    /**
     * Check if this subscriber holder has been closed.
     *
     * @return {@code true} if the holder is closed, {@code false} otherwise.
     */
    public boolean isClosed() {
        return closed.get();
    }
}
