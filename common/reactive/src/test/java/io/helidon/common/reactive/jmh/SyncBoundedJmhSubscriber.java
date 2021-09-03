/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.common.reactive.jmh;

import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.Flow;

/**
 * Subscriber for testing synchronous sources via an unbounded request amount.
 */
final class SyncBoundedJmhSubscriber implements Flow.Subscriber<Object> {

    private final Blackhole bh;

    SyncBoundedJmhSubscriber(Blackhole bh) {
        this.bh = bh;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        subscription.request(Long.MAX_VALUE - 1);
        bh.consume(subscription);
    }

    @Override
    public void onNext(Object item) {
        bh.consume(item);
    }

    @Override
    public void onError(Throwable throwable) {
        bh.consume(throwable);
    }

    @Override
    public void onComplete() {
        bh.consume(true);
    }
}
