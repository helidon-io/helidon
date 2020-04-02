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

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * Intercept the calls to the various Flow interface methods and calls the appropriate
 * user callbacks.
 * @param <T> the element type of the sequence
 */
final class MultiTappedPublisher<T> implements Multi<T> {

    private final Multi<T> source;

    private final Consumer<? super Flow.Subscription> onSubscribeCallback;

    private final Consumer<? super T> onNextCallback;

    private final Consumer<? super Throwable> onErrorCallback;

    private final Runnable onCompleteCallback;

    private final LongConsumer onRequestCallback;

    private final Runnable onCancelCallback;

    MultiTappedPublisher(Multi<T> source,
                         Consumer<? super Flow.Subscription> onSubscribeCallback,
                         Consumer<? super T> onNextCallback,
                         Consumer<? super Throwable> onErrorCallback,
                         Runnable onCompleteCallback,
                         LongConsumer onRequestCallback,
                         Runnable onCancelCallback) {
        this.source = source;
        this.onSubscribeCallback = onSubscribeCallback;
        this.onNextCallback = onNextCallback;
        this.onErrorCallback = onErrorCallback;
        this.onCompleteCallback = onCompleteCallback;
        this.onRequestCallback = onRequestCallback;
        this.onCancelCallback = onCancelCallback;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber is null");
        source.subscribe(new MultiTappedSubscriber<>(subscriber,
                onSubscribeCallback, onNextCallback,
                onErrorCallback, onCompleteCallback,
                onRequestCallback, onCancelCallback));
    }

    @Override
    public Multi<T> onComplete(Runnable onTerminate) {
        return new MultiTappedPublisher<>(
                source,
                onSubscribeCallback,
                onNextCallback,
                onErrorCallback,
                RunnableChain.combine(onCompleteCallback, onTerminate),
                onRequestCallback,
                onCancelCallback
        );
    }

    @Override
    public Multi<T> onError(Consumer<? super Throwable> onErrorConsumer) {
        return new MultiTappedPublisher<>(
                source,
                onSubscribeCallback,
                onNextCallback,
                ConsumerChain.combine(onErrorCallback, onErrorConsumer),
                onCompleteCallback,
                onRequestCallback,
                onCancelCallback
        );
    }

    @Override
    public Multi<T> onTerminate(Runnable onTerminate) {
        return new MultiTappedPublisher<>(
                source,
                onSubscribeCallback,
                onNextCallback,
                ConsumerChain.combine(onErrorCallback, e -> onTerminate.run()),
                RunnableChain.combine(onCompleteCallback, onTerminate),
                onRequestCallback,
                RunnableChain.combine(onCancelCallback, onTerminate)
        );
    }

    @Override
    public Multi<T> peek(Consumer<? super T> consumer) {
        return new MultiTappedPublisher<>(
                source,
                onSubscribeCallback,
                ConsumerChain.combine(onNextCallback, consumer),
                onErrorCallback,
                onCompleteCallback,
                onRequestCallback,
                onCancelCallback
        );
    }

    @Override
    public Multi<T> onCancel(Runnable onCancel) {
        return new MultiTappedPublisher<>(
                source,
                onSubscribeCallback,
                onNextCallback,
                onErrorCallback,
                onCompleteCallback,
                onRequestCallback,
                RunnableChain.combine(onCancelCallback, onCancel)
        );
    }

    static final class MultiTappedSubscriber<T> implements Flow.Subscriber<T>, Flow.Subscription {

        private final Flow.Subscriber<? super T> downstream;

        private final Consumer<? super Flow.Subscription> onSubscribeCallback;

        private final Consumer<? super T> onNextCallback;

        private final Consumer<? super Throwable> onErrorCallback;

        private final Runnable onCompleteCallback;

        private final LongConsumer onRequestCallback;

        private final Runnable onCancelCallback;

        private Flow.Subscription upstream;

        private boolean suppressUpstream;

        MultiTappedSubscriber(Flow.Subscriber<? super T> downstream,
                              Consumer<? super Flow.Subscription> onSubscribeCallback,
                              Consumer<? super T> onNextCallback,
                              Consumer<? super Throwable> onErrorCallback,
                              Runnable onCompleteCallback,
                              LongConsumer onRequestCallback,
                              Runnable onCancelCallback) {
            this.downstream = downstream;
            this.onSubscribeCallback = onSubscribeCallback;
            this.onNextCallback = onNextCallback;
            this.onErrorCallback = onErrorCallback;
            this.onCompleteCallback = onCompleteCallback;
            this.onRequestCallback = onRequestCallback;
            this.onCancelCallback = onCancelCallback;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            Objects.requireNonNull(subscription, "subscription is null");
            if (upstream != null) {
                subscription.cancel();
                // Microprofile RS doesn't like if this throws
                // throw new IllegalStateException("Subscription already set!");
                return;
            }
            upstream = subscription;
            if (onSubscribeCallback != null) {
                try {
                    onSubscribeCallback.accept(subscription);
                } catch (Throwable ex) {
                    upstream = SubscriptionHelper.CANCELED;
                    subscription.cancel();
                    downstream.onSubscribe(this);
                    onError(ex);
                    return;
                }
            }
            downstream.onSubscribe(this);
        }

        @Override
        public void onNext(T item) {
            if (suppressUpstream) {
                return;
            }
            if (onNextCallback != null) {
                try {
                    onNextCallback.accept(item);
                } catch (Throwable ex) {
                    upstream.cancel();
                    onError(ex);
                    return;
                }
            }
            downstream.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            if (suppressUpstream) {
                return;
            }
            suppressUpstream = true;
            if (onErrorCallback != null) {
                try {
                    onErrorCallback.accept(throwable);
                } catch (Throwable exc) {
                    throwable.addSuppressed(exc);
                }
            }
            downstream.onError(throwable);
        }

        @Override
        public void onComplete() {
            if (suppressUpstream) {
                return;
            }
            if (onCompleteCallback != null) {
                try {
                    onCompleteCallback.run();
                } catch (Throwable exc) {
                    onError(exc);
                    return;
                }
            }
            suppressUpstream = true;
            downstream.onComplete();
        }

        @Override
        public void request(long n) {
            if (onRequestCallback != null) {
                try {
                    onRequestCallback.accept(n);
                } catch (Throwable ex) {
                    fail(ex);
                }
            }
            upstream.request(n);
        }

        @Override
        public void cancel() {
            if (onCancelCallback != null) {
                try {
                    onCancelCallback.run();
                } catch (Throwable ex) {
                    fail(ex);
                }
            }
            upstream.cancel();
        }

        void fail(Throwable ex) {
            if (onErrorCallback != null) {
                try {
                    onErrorCallback.accept(ex);
                } catch (Throwable exc) {
                    // FIXME not sure where to put these
                    //  can't call onError because cancel
                    //  could be async to the rest of the
                    //  Subscriber
                }
            }
        }
    }


    /**
     * Holds a list of {@link Consumer}s to flatten out a call chain of them.
     * @param <T> the element type to accept
     */
    static final class ConsumerChain<T> extends ArrayList<Consumer<? super T>> implements Consumer<T> {

        @Override
        public void accept(T t) {
            for (Consumer<? super T> inner : this) {
                inner.accept(t);
            }
        }

        public ConsumerChain<T> combineWith(Consumer<? super T> another) {
            ConsumerChain<T> newChain = new ConsumerChain<>();
            newChain.addAll(this);
            newChain.add(another);
            return newChain;
        }

        @SuppressWarnings("unchecked")
        public static <T> Consumer<? super T> combine(Consumer<? super T> current, Consumer<? super T> another) {
            if (current == null) {
                return another;
            }
            if (another == null) {
                return current;
            }
            if (current instanceof ConsumerChain) {
                return ((ConsumerChain<T>) current).combineWith(another);
            }
            ConsumerChain<T> newChain = new ConsumerChain<>();
            newChain.add(current);
            newChain.add(another);
            return newChain;
        }
    }

    /**
     * Holds a list of {@link Runnable}s to flatten out a call chain of them.
     */
    static final class RunnableChain extends ArrayList<Runnable> implements Runnable {
        @Override
        public void run() {
            for (Runnable inner : this) {
                inner.run();
            }
        }

        public RunnableChain combineWith(Runnable another) {
            RunnableChain newChain = new RunnableChain();
            newChain.addAll(this);
            newChain.add(another);
            return newChain;
        }

        public static Runnable combine(Runnable current, Runnable another) {
            if (current == null) {
                return another;
            }
            if (another == null) {
                return current;
            }
            if (current instanceof RunnableChain) {
                return ((RunnableChain) current).combineWith(another);
            }
            RunnableChain newChain = new RunnableChain();
            newChain.add(current);
            newChain.add(another);
            return newChain;
        }
    }
}
