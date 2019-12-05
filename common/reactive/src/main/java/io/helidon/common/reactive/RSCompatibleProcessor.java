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

package io.helidon.common.reactive;

import java.util.Objects;

public class RSCompatibleProcessor<T, U> extends BaseProcessor<T, U> {

    private boolean rsCompatible = false;

    public void setRSCompatible(boolean rsCompatible) {
        this.rsCompatible = rsCompatible;
    }

    public boolean isRsCompatible() {
        return rsCompatible;
    }

    @Override
    protected boolean isSubscriberClosed() {
        // avoid checking for closed subscriber
        // https://github.com/reactive-streams/reactive-streams-jvm#2.8
        return !rsCompatible && super.isSubscriberClosed();
    }

    @Override
    protected void hookOnCancel(Flow.Subscription subscription) {
        if (rsCompatible) {
            subscription.cancel();
        }
    }

    @Override
    public void onNext(T item) {
        if (rsCompatible) {
            // https://github.com/reactive-streams/reactive-streams-jvm#2.13
            Objects.requireNonNull(item);
        }
        super.onNext(item);
    }

    @Override
    public void onSubscribe(Flow.Subscription s) {
        if (rsCompatible) {
            // https://github.com/reactive-streams/reactive-streams-jvm#2.13
            Objects.requireNonNull(s);
            // https://github.com/reactive-streams/reactive-streams-jvm#2.5
            if (Objects.nonNull(super.getSubscription())) {
                s.cancel();
            }
        }
        super.onSubscribe(s);
    }

    @Override
    public void onError(Throwable ex) {
        if (rsCompatible) {
            // https://github.com/reactive-streams/reactive-streams-jvm#2.13
            Objects.requireNonNull(ex);
        }
        super.onError(ex);
    }
}
