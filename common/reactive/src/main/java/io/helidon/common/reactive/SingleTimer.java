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

package io.helidon.common.reactive;

import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Signal 0L and complete after the specified time.
 */
final class SingleTimer extends CompletionSingle<Long> {

    private final long time;

    private final TimeUnit unit;

    private final ScheduledExecutorService executor;

    SingleTimer(long time, TimeUnit unit, ScheduledExecutorService executor) {
        this.time = time;
        this.unit = unit;
        this.executor = executor;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super Long> subscriber) {
        MultiTimer.TimerSubscription subscription = new MultiTimer.TimerSubscription(subscriber);
        subscriber.onSubscribe(subscription);

        subscription.setFuture(executor.schedule(subscription, time, unit));
    }
}
