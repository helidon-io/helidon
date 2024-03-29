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

package io.helidon.common.reactive;

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Allows changing the subscription, tracking requests and item
 * production in concurrent source-switching scenarios.
 * <p>
 *     {@code this} is the work-in-progress indicator for the
 *     subscriber-request-produced trampolining.
 * </p>
 * <p>
 *     Please override {@link #request(long)} and perform the n &lt;= 0L
 *     check in the context of the implementor because the TCK requires
 *     an onError signal and the arbiter has no contextual knowledge how
 *     and when to signal it.
 * </p>
 */
class SubscriptionArbiter
        extends AtomicInteger
        implements Flow.Subscription {

    /** The current outstanding request amount. */
    private long requested;

    /** The current subscription to relay requests for. */
    private Flow.Subscription subscription;

    /** The new subscription to use. */
    private final AtomicReference<Flow.Subscription> newSubscription;

    /** Requests accumulated. */
    private final AtomicLong newRequested;

    /** Item production count accumulated. */
    private final AtomicLong newProduced;

    /**
     * Constructs an empty arbiter.
     */
    protected SubscriptionArbiter() {
        this.newProduced  = new AtomicLong();
        this.newRequested = new AtomicLong();
        this.newSubscription = new AtomicReference<>();
    }

    @Override
    public void request(long n) {
        SubscriptionHelper.addRequest(newRequested, n);
        drain();
    }

    @Override
    public void cancel() {
        SubscriptionHelper.cancel(newSubscription);
        drain();
    }

    /**
     * Set the new subscription to resume with.
     * @param subscription the new subscription
     * @throws NullPointerException if {@code subscription} is {@code null}
     */
    protected void setSubscription(Flow.Subscription subscription) {
        Objects.requireNonNull(subscription, "subscription is null");
        for (;;) {
            Flow.Subscription previous = newSubscription.get();
            if (previous == SubscriptionHelper.CANCELED) {
                subscription.cancel();
                return;
            }
            if (newSubscription.compareAndSet(previous, subscription)) {
                break;
            }
        }
        drain();
    }

    /**
     * Indicate how many items were produced from the current subscription
     * before switching to the next subscription.
     * @param n the number of items produced, positive
     */
    protected void produced(long n) {
        SubscriptionHelper.addRequest(newProduced, n);
        drain();
    }

    final void drain() {
        if (getAndIncrement() != 0) {
            return;
        }
        long toRequest = 0L;
        Flow.Subscription requestFrom = null;

        do {
            long req = newRequested.get();
            if (req != 0L) {
                req = newRequested.getAndSet(0L);
            }
            long prod = newProduced.get();
            if (prod != 0L) {
                prod = newProduced.getAndSet(0L);
            }
            Flow.Subscription next = newSubscription.get();
            boolean isCanceled = next == SubscriptionHelper.CANCELED;
            if (next != null) {
                newSubscription.compareAndSet(next, null);
            }

            if (isCanceled) {
                Flow.Subscription s = subscription;
                subscription = null;
                if (s != null) {
                    s.cancel();
                }
                toRequest = 0L;
                requestFrom = null;
            } else {
                long currentRequested = requested;

                if (req != 0L) {
                    currentRequested += req;
                    if (currentRequested < 0L) {
                        currentRequested = Long.MAX_VALUE;
                    }
                    toRequest += req;
                    if (toRequest < 0L) {
                        toRequest = Long.MAX_VALUE;
                    }
                    requestFrom = subscription;
                }
                if (prod != 0L && currentRequested != Long.MAX_VALUE) {
                    currentRequested -= prod;
                    if (currentRequested < 0L) {
                        currentRequested = 0L;
                    }
                }
                if (next != null) {
                    subscription = next;
                    requestFrom = next;
                    toRequest = currentRequested;
                }
                requested = currentRequested;
            }

        } while (decrementAndGet() != 0);

        // request outside the serialization loop to avoid certain reentrance issues
        if (requestFrom != null && toRequest != 0L) {
            requestFrom.request(toRequest);
        }
    }

    /**
     * Checks if this arbiter, and all its hosted subscriptions,
     * have been canceled.
     * @return true if canceled
     */
    protected final boolean isCanceled() {
        return newSubscription.get() == SubscriptionHelper.CANCELED;
    }
}
