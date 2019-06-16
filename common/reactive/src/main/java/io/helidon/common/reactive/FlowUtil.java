/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

/**
 * Flow API utilities.
 */
public final class FlowUtil {
    private static final Flow.Publisher<?> EMPTY_PUBLISHER = new EmptyPublisher();

    private FlowUtil() {
        throw new IllegalStateException("This is a utility class");
    }

    /**
     * Create an empty publisher that immediately completes the subscription on request.
     * @param <T> type of the publisher to create
     * @return a new publisher that just completes
     */
    @SuppressWarnings("unchecked")
    public static <T> Flow.Publisher<T> emptyPublisher() {
        return (Flow.Publisher<T>) EMPTY_PUBLISHER;
    }

    private static class EmptyPublisher implements Flow.Publisher<Object> {
        @Override
        public void subscribe(Flow.Subscriber<? super Object> subscriber) {
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    subscriber.onComplete();
                }

                @Override
                public void cancel() {

                }
            });
        }

        @Override
        public String toString() {
            return "FlowUtil.EmptyPublisher";
        }
    }
}
