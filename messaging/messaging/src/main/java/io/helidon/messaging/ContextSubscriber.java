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
 *
 */

package io.helidon.messaging;

import java.util.UUID;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

class ContextSubscriber<T> implements Subscriber<T> {

    private final String prefix;
    private final Subscriber<T> subscriber;

    ContextSubscriber(final String prefix, final Subscriber<T> subscriber) {
        this.prefix = prefix;
        this.subscriber = subscriber;
    }

    static <T> ContextSubscriber<T> create(String prefix, Subscriber<T> subscriber) {
        return new ContextSubscriber<>(prefix, subscriber);
    }

    @Override
    public void onSubscribe(final Subscription s) {
        subscriber.onSubscribe(s);
    }

    @Override
    public void onNext(final T item) {
        runInNewContext(() -> subscriber.onNext(item));
    }

    @Override
    public void onError(final Throwable t) {
        runInNewContext(() -> subscriber.onError(t));
    }

    @Override
    public void onComplete() {
        runInNewContext(subscriber::onComplete);
    }

    void runInNewContext(Runnable runnable) {
        Context.Builder contextBuilder = Context.builder()
                .id(String.format("%s-%s:", prefix, UUID.randomUUID().toString()));
        Contexts.context().ifPresent(contextBuilder::parent);
        Contexts.runInContext(contextBuilder.build(), runnable);
    }
}
