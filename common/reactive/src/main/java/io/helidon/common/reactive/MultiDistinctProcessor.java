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

import java.util.HashSet;
import java.util.concurrent.Flow;

/**
 * Filter out all duplicate items.
 *
 * @param <T> item type
 */
public class MultiDistinctProcessor<T> extends BufferedProcessor<T, T> implements Multi<T> {
    private final HashSet<T> distinctSet;

    /**
     * Create new {@link MultiDistinctProcessor}.
     */
    public MultiDistinctProcessor() {
        this.distinctSet = new HashSet<T>();
    }

    @Override
    protected void hookOnCancel(Flow.Subscription subscription) {
        subscription.cancel();
    }

    @Override
    protected void hookOnNext(T item) {
        if (!distinctSet.contains(item)) {
            distinctSet.add(item);
            submit(item);
        } else {
            tryRequest(getSubscription().get());
        }
    }
}
