/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * The WrappedOriginThreadPublisher.
 */
public class WrappedOriginThreadPublisher
        extends OriginThreadPublisher<CharSequence, byte[]>
        implements Publisher<CharSequence> {

    WrappedOriginThreadPublisher(UnboundedSemaphore semaphore) {
        super(semaphore);
    }

    @Override
    public void subscribe(Subscriber<? super CharSequence> subscriber) {
        super.subscribe(new Flow.Subscriber<CharSequence>() {
            @Override
            public void onSubscribe(Flow.Subscription flowSubscription) {
                subscriber.onSubscribe(new Subscription() {
                    @Override
                    public void request(long n) {
                        flowSubscription.request(n);
                    }

                    @Override
                    public void cancel() {
                        flowSubscription.cancel();
                    }
                });
            }

            @Override
            public void onNext(CharSequence item) {
                subscriber.onNext(item);
            }

            @Override
            public void onError(Throwable throwable) {
                subscriber.onError(throwable);
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
            }
        });
    }

    @Override
    protected CharSequence wrap(byte[] item) {
        return new String(item);
    }
}
