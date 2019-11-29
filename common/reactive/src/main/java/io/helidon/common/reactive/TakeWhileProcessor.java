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

import java.util.function.Predicate;

public class TakeWhileProcessor<T> extends BaseProcessor<T, T> implements Multi<T> {
    private Predicate<T> predicate;

    public TakeWhileProcessor(Predicate<T> predicate) {
        this.predicate = predicate;
    }

    @Override
    protected void hookOnNext(T item) {
        try {
            if (predicate.test(item)) {
                submit(item);
            } else {
                tryComplete();
            }
        } catch (Throwable t) {
            getSubscription().cancel();
            onError(t);
        }
    }

    @Override
    protected void hookOnCancel(Flow.Subscription subscription) {
        subscription.cancel();
    }
}
