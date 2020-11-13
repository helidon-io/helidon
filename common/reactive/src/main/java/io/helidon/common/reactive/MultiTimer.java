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
 *
 */

package io.helidon.common.reactive;

import java.util.concurrent.Callable;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Signal 0L and complete after the specified time.
 */
final class MultiTimer implements Multi<Long> {

    private final long time;

    private final TimeUnit unit;

    private final ScheduledExecutorService executor;

    MultiTimer(long time, TimeUnit unit, ScheduledExecutorService executor) {
        this.time = time;
        this.unit = unit;
        this.executor = executor;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super Long> subscriber) {
        TimerSubscription subscription = new TimerSubscription(subscriber);
        subscriber.onSubscribe(subscription);

        subscription.setFuture(executor.schedule(subscription, time, unit));
    }

    static final class TimerSubscription extends DeferredScalarSubscription<Long>
    implements Callable<Void> {

        private final AtomicReference<Future<?>> future;

        TimerSubscription(Flow.Subscriber<? super Long> downstream) {
            super(downstream);
            this.future = new AtomicReference<>();
        }

        @Override
        public Void call() {
            future.lazySet(TerminatedFuture.FINISHED);
            complete(0L);
            return null;
        }

        @Override
        public void cancel() {
            super.cancel();
            TerminatedFuture.cancel(future);
        }

        public void setFuture(Future<?> f) {
            TerminatedFuture.setFuture(future, f);
        }
    }

}
