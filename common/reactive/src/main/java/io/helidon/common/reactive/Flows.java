/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.Flow;

/**
 * Utilities for Flow API.
 */
public final class Flows {
    private Flows() {
    }

    /**
     * Empty publisher.
     *
     * @param <T> type of the publisher
     * @return a new empty publisher that just completes the subscriber
     */
    public static <T> Flow.Publisher<T> emptyPublisher() {
        return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
                subscriber.onComplete();
            }

            @Override
            public void cancel() {
            }
        });
    }

    /**
     * A publisher of a single value.
     *
     * @param value value to publish
     * @param <T> type of the publisher
     * @return a new publisher that publishes the single value and completes the subscriber
     */
    public static <T> Flow.Publisher<T> singletonPublisher(T value) {
        return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
                subscriber.onNext(value);
                subscriber.onComplete();
            }

            @Override
            public void cancel() {
            }
        });
    }
}
