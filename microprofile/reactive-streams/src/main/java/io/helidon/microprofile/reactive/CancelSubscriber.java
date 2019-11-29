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

package io.helidon.microprofile.reactive;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.reactive.Flow;

public class CancelSubscriber implements Flow.Subscriber<Object> {

    AtomicBoolean cancelled = new AtomicBoolean(false);

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        if (cancelled.compareAndSet(false, true)) {
            subscription.cancel();
        } else {
            throw new CancellationException();
        }
    }

    @Override
    public void onNext(Object item) {
    }

    @Override
    public void onError(Throwable throwable) {
        // Cancel ignores upstream failures
    }

    @Override
    public void onComplete() {
    }
}
