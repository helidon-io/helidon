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

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;

/**
 * Static utility methods for converting between Helidon reactive API {@link Flow} and
 * <a href="http://www.reactive-streams.org/">reactive-streams</a>.
 *
 * @deprecated This class will be removed in the next major release.
 */
@Deprecated
public final class ReactiveStreamsAdapter {

    private ReactiveStreamsAdapter() {} // uninstantiable

    /**
     * Return a {@link Flow.Publisher} from a {@link
     * org.reactivestreams.Publisher}.
     *
     * @param publisher the source Publisher to convert
     * @param <T> the type of the publisher
     * @return a {@link Flow.Publisher}
     */
    public static <T> Flow.Publisher<T> publisherToFlow(final Publisher<T> publisher) {
        return new FlowPublisher<>(publisher);
    }

    /**
     * Return a {@link org.reactivestreams.Publisher} from a {@link
     * Flow.Publisher}.
     *
     * @param publisher the source Publisher to convert
     * @param <T> the type of the publisher
     * @return a {@link reactor.core.publisher.Flux}
     */
    public static <T> Flux<T> publisherFromFlow(Flow.Publisher<T> publisher) {
        return new ReactiveStreamsPublisher<>(publisher);
    }

    /**
     * Return a {@link Flow.Subscriber} from a {@link
     * org.reactivestreams.Subscriber}.
     * @param <T> the type of the subscriber
     * @param subscriber the source Subscriber to convert
     * @return a {@link org.reactivestreams.Subscriber}
     */
    public static <T> Flow.Subscriber<T> subscriberToFlow(final Subscriber<T> subscriber) {
        return new FlowSubscriber<>(subscriber);
    }

    /**
     * Return a {@link org.reactivestreams.Subscriber} from a {@link
     * Flow.Subscriber}.
     * @param <T> the type of the subscriber
     * @param subscriber the source Subscriber to convert
     * @return a {@link Flow.Subscriber}
     */
    public static <T> Subscriber<T> subscriberFromFlow(final Flow.Subscriber<T> subscriber) {
        return new ReactiveStreamsSubscriber<>(subscriber);
    }

    private static class ReactiveStreamsPublisher<T> extends Flux<T> {

        private final Flow.Publisher<T> pub;

        private ReactiveStreamsPublisher(Flow.Publisher<T> pub) {
            this.pub = pub;
        }

        @Override
        public void subscribe(final CoreSubscriber<? super T> actual) {
            pub.subscribe(new FlowSubscriber<>(actual));
        }
    }

    private static class FlowPublisher<T> implements Flow.Publisher<T> {

        private final Publisher<T> pub;

        private FlowPublisher(Publisher<T> pub) {
            this.pub = pub;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super T> subscriber) {
            pub.subscribe(new ReactiveStreamsSubscriber<>(subscriber));
        }
    }

    private static class ReactiveStreamsSubscriber<T> implements CoreSubscriber<T>, Flow.Subscription {

        private final Flow.Subscriber<? super T> subscriber;
        private Subscription subscription;

        ReactiveStreamsSubscriber(Flow.Subscriber<? super T> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void onSubscribe(final Subscription s) {
            this.subscription = s;
            subscriber.onSubscribe(this);
        }

        @Override
        public void onNext(T o) {
            subscriber.onNext(o);
        }

        @Override
        public void onError(Throwable t) {
            subscriber.onError(t);
        }

        @Override
        public void onComplete() {
            subscriber.onComplete();
        }

        @Override
        public void request(long n) {
            subscription.request(n);
        }

        @Override
        public void cancel() {
            subscription.cancel();
        }
    }

    private static class FlowSubscriber<T> implements Flow.Subscriber<T>, Subscription {

        private final Subscriber<? super T> s;

        private Flow.Subscription subscription;

        FlowSubscriber(Subscriber<? super T> s) {
            this.s = s;
        }

        @Override
        public void onSubscribe(final Flow.Subscription subscription) {
            this.subscription = subscription;
            s.onSubscribe(this);
        }

        @Override
        public void onNext(T o) {
            s.onNext(o);
        }

        @Override
        public void onError(Throwable throwable) {
            s.onError(throwable);
        }

        @Override
        public void onComplete() {
            s.onComplete();
        }

        @Override
        public void request(long n) {
            subscription.request(n);
        }

        @Override
        public void cancel() {
            subscription.cancel();
        }
    }
}
