/*
 * Copyright (c)  2019 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.common.reactive.Flow;
import org.reactivestreams.Subscription;

import java.security.InvalidParameterException;

public class HybridSubscription implements Flow.Subscription, Subscription {

    Flow.Subscription flowSubscription;
    Subscription reactiveSubscription;

    private HybridSubscription(Flow.Subscription flowSubscription) {
        this.flowSubscription = flowSubscription;
    }

    private HybridSubscription(Subscription reactiveSubscription) {
        this.reactiveSubscription = reactiveSubscription;
    }

    public static HybridSubscription from(Flow.Subscription subscription) {
        return new HybridSubscription(subscription);
    }

    public static HybridSubscription from(Subscription subscription) {
        return new HybridSubscription(subscription);
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
    }
}
