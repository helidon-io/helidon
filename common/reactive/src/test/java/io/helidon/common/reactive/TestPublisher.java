/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.common.reactive.Flow.Publisher;
import io.helidon.common.reactive.Flow.Subscriber;
import io.helidon.common.reactive.Flow.Subscription;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Dummy items publisher for testing purpose.
 *
 * @param <T> item type
 */
public class TestPublisher<T> implements Publisher<T> {

    private final T[] items;
    boolean subscribed;

    @SafeVarargs
    TestPublisher(T... items) {
        this.items = items;
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        subscribed = true;
        subscriber.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
                if (n > 0) {
                    Queue<T> items = new LinkedList<>(Arrays.asList(TestPublisher.this.items));
                    for (; n > 0 && !items.isEmpty(); n--) {
                        subscriber.onNext(items.poll());
                    }
                    if (items.isEmpty()) {
                        subscriber.onComplete();
                    }
                }
            }

            @Override
            public void cancel() {
            }
        });
    }
}
