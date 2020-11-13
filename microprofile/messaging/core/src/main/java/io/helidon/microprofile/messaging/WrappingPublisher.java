/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.messaging;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

class WrappingPublisher implements Publisher<Object> {

    private final Publisher<?> source;

    WrappingPublisher(Publisher<?> source) {
        this.source = source;
    }

    @Override
    public void subscribe(final Subscriber<? super Object> s) {
        source.subscribe(new WrappingSubscriber(s));
    }

    private static class WrappingSubscriber implements Subscriber<Object> {

        private final Subscriber<Object> sourceSubscriber;

        WrappingSubscriber(Subscriber<Object> sourceSubscriber) {
            this.sourceSubscriber = sourceSubscriber;
        }

        @Override
        public void onSubscribe(final Subscription s) {
            this.sourceSubscriber.onSubscribe(s);
        }

        @Override
        public void onNext(final Object t) {
            if (t instanceof Message) {
                this.sourceSubscriber.onNext(t);
                return;
            }
            this.sourceSubscriber.onNext(Message.of(t));
        }

        @Override
        public void onError(final Throwable t) {
            this.sourceSubscriber.onError(t);
        }

        @Override
        public void onComplete() {
            this.sourceSubscriber.onComplete();
        }
    }
}
