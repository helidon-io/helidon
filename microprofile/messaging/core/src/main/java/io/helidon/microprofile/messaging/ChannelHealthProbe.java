/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.messaging;

import java.util.concurrent.atomic.AtomicBoolean;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Tap between publisher and subscriber to register if onError or cancel has been intercepted.
 */
class ChannelHealthProbe {

    private final AtomicBoolean live;
    private final AtomicBoolean ready;

    private ChannelHealthProbe(AtomicBoolean live, AtomicBoolean ready) {
        this.live = live;
        this.ready = ready;
    }

    void connect(Publisher<?> pub, Subscriber<? super Object> sub) {
        pub.subscribe(new Subscriber<Object>() {
            @Override
            public void onSubscribe(final Subscription s) {
                sub.onSubscribe(new Subscription() {
                    @Override
                    public void request(final long n) {
                        s.request(n);
                    }

                    @Override
                    public void cancel() {
                        live.set(false);
                        s.cancel();
                    }
                });
                ready.set(true);
            }

            @Override
            public void onNext(final Object o) {
                sub.onNext(o);
            }

            @Override
            public void onError(final Throwable t) {
                live.set(false);
                sub.onError(t);
            }

            @Override
            public void onComplete() {
                sub.onComplete();
            }
        });
    }

    static ChannelHealthProbe create(AtomicBoolean live, AtomicBoolean ready) {
        return new ChannelHealthProbe(live, ready);
    }

}
