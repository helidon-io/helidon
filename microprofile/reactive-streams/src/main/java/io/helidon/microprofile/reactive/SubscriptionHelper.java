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
 */

package io.helidon.microprofile.reactive;

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Helper enum with a singleton cancellation indicator and utility methods to perform
 * atomic actions on {@link Flow.Subscription}s.
 */
enum SubscriptionHelper implements Flow.Subscription {
    /**
     * The singleton instance indicating a canceled subscription.
     */
    CANCELED, EMPTY;

    @Override
    public void request(long n) {
        // deliberately no-op.
    }

    @Override
    public void cancel() {
        // deliberately no-op
    }

    /**
     * Atomically add the given request amount to the field while capping it at
     * {@link Long#MAX_VALUE}.
     * @param field the target field to update
     * @param n the request amount to add, must be positive (not verified)
     * @return the old request amount after the operation
     */
    public static long addRequest(AtomicLong field, long n) {
        for (;;) {
            long current = field.get();
            if (current == Long.MAX_VALUE) {
                return Long.MAX_VALUE;
            }
            long update = current + n;
            if (update < 0L) {
                update = Long.MAX_VALUE;
            }
            if (field.compareAndSet(current, update)) {
                return current;
            }
        }
    }

    /**
     * Atomically sets the only upstream subscription in the field.
     * @param subscriptionField the field to store the only upstream subscription
     * @param upstream the only upstream to set and request from
     * @return true if the operation succeeded, false if the field holds the cancellation indicator
     * @throws IllegalStateException if the subscriptionField already contains a non-canceled subscription instance
     */
    public static boolean setOnce(AtomicReference<Flow.Subscription> subscriptionField, Flow.Subscription upstream) {
        Objects.requireNonNull(upstream);
        for (;;) {
            Flow.Subscription current = subscriptionField.get();
            if (current == CANCELED) {
                upstream.cancel();
                return false;
            }
            if (current != null) {
                upstream.cancel();
                //throw new IllegalStateException("Flow.Subscription already set.");
                return false;
            }

            if (subscriptionField.compareAndSet(null, upstream)) {
                return true;
            }
        }
    }

    /**
     * Atomically sets the only upstream subscription in the field and then requests
     * the amount accumulated in the requestedField.
     * @param subscriptionField the field to store the only upstream subscription
     * @param requestedField the request amounts accumulated so far
     * @param upstream the only upstream to set and request from
     * @return true if the operation succeeded, false if the field indicated the upstream
     *         should be cancelled immediately
     * @throws IllegalStateException if the subscriptionField already contains a non-canceled subscription instance
     */
    public static boolean deferredSetOnce(AtomicReference<Flow.Subscription> subscriptionField,
                                          AtomicLong requestedField, Flow.Subscription upstream) {
        if (setOnce(subscriptionField, upstream)) {
            long requested = requestedField.getAndSet(0L);
            if (requested != 0L) {
                upstream.request(requested);
            }
            return true;
        }
        return false;
    }

    /**
     * Accumulates request amounts until the subscription field receives a Subscription instance,
     * then requests this accumulated amount and forwards subsequent requests to it.
     * @param subscriptionField the field possibly containing a Subscription instance.
     * @param requestedField the field used for accumulating requests until the Subscription instance arrives
     * @param n the request amount to accumulate or forward, must be positive (not verified)
     */
    public static void deferredRequest(AtomicReference<Flow.Subscription> subscriptionField,
                                       AtomicLong requestedField, long n) {
        Flow.Subscription subscription = subscriptionField.get();
        if (subscription != null) {
            subscription.request(n);
        } else {
            addRequest(requestedField, n);
            subscription = subscriptionField.get();
            if (subscription != null) {
                long toRequest = requestedField.getAndSet(0L);
                if (toRequest != 0L) {
                    subscription.request(toRequest);
                }
            }
        }
    }

    /**
     * Atomically swap in the {@link #CANCELED} instance and call cancel() on
     * any previous Subscription held.
     * @param subscriptionField the target field to cancel atomically.
     * @return true if the current thread succeeded with the cancellation (as only one thread is able to)
     */
    public static boolean cancel(AtomicReference<Flow.Subscription> subscriptionField) {
        Flow.Subscription subscription = subscriptionField.get();
        if (subscription != CANCELED) {
            subscription = subscriptionField.getAndSet(CANCELED);
            if (subscription != CANCELED) {
                if (subscription != null) {
                    subscription.cancel();
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Check if current is null and incoming is not null.
     * @param current the current subscription, should be null
     * @param incoming the incoming subscription, should be non-null
     * @throws IllegalStateException if current is not-null indicating a bug in an operator calling onSubscribe
     *                               more than once
     */
    public static void validate(Flow.Subscription current, Flow.Subscription incoming) {
        Objects.requireNonNull(incoming);
        if (current != null) {
            incoming.cancel();
            throw new IllegalStateException("Flow.Subscription already set.");
        }
    }
}
