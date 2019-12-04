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

import java.util.concurrent.atomic.AtomicLong;

public class SkipProcessor<T> extends RSCompatibleProcessor<T, T> implements Multi<T> {

    private final AtomicLong counter;

    /**
     * Processor skips first n items, all the others are emitted.
     *
     * @param skip number of items to be skipped
     */
    public SkipProcessor(Long skip) {
        counter = new AtomicLong(skip);
    }

    @Override
    protected void tryRequest(Flow.Subscription s) {
        if (s != null && !getSubscriber().isClosed()) {
            long n = getRequestedCounter().get();
            if (n > 0) {
                //Request one by one with skip
                s.request(1);
            }
        }
    }

    @Override
    protected void hookOnNext(T item) {
        long actCounter = this.counter.getAndDecrement();
        if (0 >= actCounter) {
            submit(item);
        } else {
            getRequestedCounter().tryDecrement();
        }
        getRequestedCounter().increment(1, this::onError);
        tryRequest(getSubscription());
    }
}
