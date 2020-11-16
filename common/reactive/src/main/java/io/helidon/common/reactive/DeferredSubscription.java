/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Used for deferring any callbacks caused by calling onSubscribe
 * until control is returned.
 *
 * <pre>{@code
 *         DeferredSubscription ds = new DeferredSubscription();
 *         subscriber.onSubscribe(ds);
 *         // request/cancel signals received until now are going to be processed
 *         // after setting actual subscription
 *         ds.setSubscription(subscription);
 * }</pre>
 * See spec Rule §1.3 for more info.
 */
class DeferredSubscription extends AtomicReference<Flow.Subscription>
        implements Flow.Subscription {

    private static final long serialVersionUID = -6510169867323964352L;
    private final AtomicLong requested = new AtomicLong();

    @Override
    public void request(long n) {
        if (n <= 0L && SubscriptionHelper.badRequest(this)) {
            //subscription ref is not null, deferredRequest wont increment any more
            n = -1L;
            requested.set(n);
        }
        SubscriptionHelper.deferredRequest(this, requested, n);
    }

    @Override
    public void cancel() {
        SubscriptionHelper.cancel(this);
    }

    void setSubscription(Flow.Subscription s) {
        SubscriptionHelper.deferredSetOnce(this, requested, s);
    }
}
