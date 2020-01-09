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

package io.helidon.common.reactive;

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Let pass only specified number of items.
 *
 * @param <T> both input/output type
 */
public class MultiLimitProcessor<T> extends BufferedProcessor<T, T> implements Multi<T> {

    private final AtomicLong counter;

    private MultiLimitProcessor(Long limit) {
        counter = new AtomicLong(limit);
    }

    /**
     * Processor with specified number of allowed items.
     *
     * @param limit number of items to pass
     * @param <T>   both input/output type
     * @return {@link MultiLimitProcessor}
     */
    public static <T> MultiLimitProcessor<T> create(Long limit) {
        return new MultiLimitProcessor<>(limit);
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> s) {
        super.subscribe(s);
        if (counter.get() == 0L) {
            tryComplete();
        }
    }

    @Override
    public void onError(Throwable ex) {
        if (0 < this.counter.get()) {
            super.onError(ex);
        } else {
            tryComplete();
        }
    }

    @Override
    protected void hookOnNext(T item) {
        long actCounter = this.counter.getAndDecrement();
        if (0 < actCounter) {
            submit(item);
        } else {
            getSubscription().ifPresent(Flow.Subscription::cancel);
            tryComplete();
        }
    }

    @Override
    public String toString() {
        return "LimitProcessor{" + "counter=" + counter + '}';
    }
}
