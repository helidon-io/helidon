/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.messaging.connectors.mock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.helidon.common.reactive.Single;
import io.helidon.common.reactive.SubscriptionHelper;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

class MockSubscriber implements Subscriber<Message<?>> {

    private final AtomicReference<Flow.Subscription> upstream = new AtomicReference<>();
    private final List<Message<?>> items = new CopyOnWriteArrayList<>();
    private final CompletableFuture<Void> completed = new CompletableFuture<>();
    private final List<Consumer<Integer>> counters = Collections.synchronizedList(new ArrayList<>());
    private final List<Consumer<Message<?>>> checkers = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void onSubscribe(Subscription subscription) {
        SubscriptionHelper.setOnce(upstream, new Flow.Subscription() {
            @Override
            public void request(long n) {
                if (n <= 0L) {
                    onError(new IllegalArgumentException("Rule §3.9 violated: non-positive requests are forbidden!"));
                } else {
                    subscription.request(n);
                }
            }

            @Override
            public void cancel() {
                SubscriptionHelper.cancel(upstream);
            }
        });
    }

    @Override
    public void onNext(Message<?> item) {
        items.add(item);
        counters.forEach(c -> c.accept(items.size()));
        checkers.forEach(c -> c.accept(item));
    }

    @Override
    public void onError(Throwable throwable) {
        completed.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
        completed.complete(null);
    }

    List<Message<?>> data(){
        return Collections.unmodifiableList(items);
    }

    List<Consumer<Integer>> counters() {
        return counters;
    }

    List<Consumer<Message<?>>> checkers() {
        return checkers;
    }

    Flow.Subscription upstream(){
        return upstream.get();
    }

    Single<Void> completed(){
        return Single.create(completed, true);
    }
}
