/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.microprofile.reactive;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cancel the upstream immediately upon subscription and complete a CompletableFuture.
 * @param <T> the element type of the upstream
 */
final class BasicCancelSubscriber<T>
extends AtomicReference<Flow.Subscription>
implements Flow.Subscriber<T> {

    private static final long serialVersionUID = -1718297417587197143L;

    private final CompletableFuture<T> completable = new CompletableFuture<>();

    CompletableFuture<T> completable() {
        return completable;
    }

    @Override
    public void onSubscribe(Flow.Subscription s) {
        if (SubscriptionHelper.setOnce(this, s)) {
            SubscriptionHelper.cancel(this);
            completable.complete(null);
        }
    }

    @Override
    public void onNext(T t) {
        Objects.requireNonNull(t, "t is null");
    }

    @Override
    public void onError(Throwable t) {
        Objects.requireNonNull(t, "t is null");
        // ignored for now
    }

    @Override
    public void onComplete() {
        // ignored
    }


    // Workaround for SpotBugs, Flow classes should never get serialized
    private void writeObject(ObjectOutputStream stream)
            throws IOException {
        stream.defaultWriteObject();
    }

    // Workaround for SpotBugs, Flow classes should never get serialized
    private void readObject(ObjectInputStream stream)
            throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
    }
}
