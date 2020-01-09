/*
 * Copyright (c)  2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.reactive.hybrid;

import java.security.InvalidParameterException;
import java.util.Optional;
import java.util.concurrent.Flow;

import org.reactivestreams.Subscription;

/**
 * Wrapper for {@link org.reactivestreams Reactive Streams} {@link org.reactivestreams.Subscription}
 * or {@link io.helidon.common.reactive Helidon reactive streams} {@link java.util.concurrent.Flow.Subscription},
 * to be used interchangeably.
 */
public class HybridSubscription implements Flow.Subscription, Subscription {

    private Flow.Subscription flowSubscription;
    private Subscription reactiveSubscription;
    private Optional<Runnable> onCancel = Optional.empty();

    private HybridSubscription(Flow.Subscription flowSubscription) {
        this.flowSubscription = flowSubscription;
    }

    private HybridSubscription(Subscription reactiveSubscription) {
        this.reactiveSubscription = reactiveSubscription;
    }

    /**
     * Create new {@link HybridSubscription}
     * from {@link java.util.concurrent.Flow.Processor}.
     *
     * @param subscription {@link java.util.concurrent.Flow.Subscription} to wrap
     * @return {@link HybridSubscription}
     * compatible with {@link org.reactivestreams Reactive Streams}
     * and {@link io.helidon.common.reactive Helidon reactive streams}
     */
    public static HybridSubscription from(Flow.Subscription subscription) {
        return new HybridSubscription(subscription);
    }

    /**
     * Create new {@link HybridSubscription}
     * from {@link java.util.concurrent.Flow.Subscription}.
     *
     * @param subscription {@link java.util.concurrent.Flow.Subscription} to wrap
     * @return {@link HybridSubscription}
     * compatible with {@link org.reactivestreams Reactive Streams}
     * and {@link io.helidon.common.reactive Helidon reactive streams}
     */
    public static HybridSubscription from(Subscription subscription) {
        return new HybridSubscription(subscription);
    }

    HybridSubscription onCancel(Runnable runnable) {
        this.onCancel = Optional.of(runnable);
        return this;
    }

    @Override
    public void request(long n) {
        if (flowSubscription != null) {
            flowSubscription.request(n);
        } else if (reactiveSubscription != null) {
            reactiveSubscription.request(n);
        } else {
            throw new InvalidParameterException("Hybrid subscription has no subscription");
        }
    }

    @Override
    public void cancel() {
        if (flowSubscription != null) {
            flowSubscription.cancel();
        } else if (reactiveSubscription != null) {
            reactiveSubscription.cancel();
        } else {
            throw new InvalidParameterException("Hybrid subscription has no subscription");
        }
        onCancel.ifPresent(Runnable::run);
    }
}
