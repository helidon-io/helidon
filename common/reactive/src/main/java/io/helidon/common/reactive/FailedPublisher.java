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

public class FailedPublisher<T> implements Flow.Publisher<T>, Multi<T> {

    private Throwable throwable;

    public FailedPublisher(Throwable throwable) {
        this.throwable = throwable;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
                subscriber.onError(throwable);
            }

            @Override
            public void cancel() {

            }
        });
        subscriber.onError(throwable);
    }
}
