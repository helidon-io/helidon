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

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Flatten the elements emitted by publishers produced by the mapper function to this stream.
 *
 * @param <T> input item type
 * @param <X> output item type
 */
public class MultiFlatMapProcessor<T, X> implements Flow.Processor<T, X>, Multi<X> {

    private Function<T, Flow.Publisher<X>> mapper;
    private final AtomicInteger active = new AtomicInteger(NOT_STARTED | INNER_COMPLETE);
    private final Inner inner = new Inner();

    private Function<T, Flow.Publisher<X>> mapper;
    private SubscriberReference<? super X> subscriber;
    private Flow.Subscription subscription;
    private RequestedCounter requestCounter = new RequestedCounter();
    private Flow.Subscription innerSubscription;
    private AtomicBoolean onCompleteReceivedAlready = new AtomicBoolean(false);
    private PublisherBuffer<T> buffer;
    private Optional<Throwable> error = Optional.empty();

            int a;
            do {
                a = active.get();
                if ((a & NOT_STARTED) == 0) {
                    return;
                }
            } while (!active.compareAndSet(a, a - NOT_STARTED));

            getSubscription().request(1);
        }

        @Override
        public void cancel() {
        }
    });

    /**
     * Create new {@link MultiFlatMapProcessor}.
     */
    protected MultiFlatMapProcessor() {
    }

    /**
     * Create new {@link MultiFlatMapProcessor} with item to {@link java.lang.Iterable} mapper.
     *
     * @param mapper to provide iterable for every item from upstream
     * @param <T>    input item type
     * @param <R>    output item type
     * @return {@link MultiFlatMapProcessor}
     */
    @SuppressWarnings("unchecked")
    public static <T, R> MultiFlatMapProcessor<T, R> fromIterableMapper(Function<T, Iterable<R>> mapper) {
        MultiFlatMapProcessor<T, R> flatMapProcessor = new MultiFlatMapProcessor<>();
        flatMapProcessor.mapper = o -> (Multi<R>) Multi.from(mapper.apply(o));
        return flatMapProcessor;
    }

    /**
     * Create new {@link MultiFlatMapProcessor} with item to {@link java.util.concurrent.Flow.Publisher} mapper.
     *
     * @param mapper to provide iterable for every item from upstream
     * @param <T>    input item type
     * @param <U>    output item type
     * @return {@link MultiFlatMapProcessor}
     */
    @SuppressWarnings("unchecked")
    public static <T, U> MultiFlatMapProcessor<T, U> fromPublisherMapper(Function<T, Flow.Publisher<U>> mapper) {
        MultiFlatMapProcessor<T, U> flatMapProcessor = new MultiFlatMapProcessor<>();
        flatMapProcessor.mapper = t -> (Flow.Publisher<U>) mapper.apply(t);
        return flatMapProcessor;
    }


    /**
     * Set mapper used for publisher creation.
     *
     * @param mapper function used for publisher creation
     * @return {@link MultiFlatMapProcessor}
     */
    protected MultiFlatMapProcessor<T, X> mapper(Function<T, Flow.Publisher<X>> mapper) {
        Objects.requireNonNull(mapper);
        this.mapper = mapper;
        return this;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super X> subscriber) {
        this.subscriber = SubscriberReference.create(subscriber);
        if (Objects.nonNull(this.subscription)) {
            subscriber.onSubscribe(new FlatMapSubscription());
        }
    }

    @Override
    protected void complete() {
        // if INNER_COMPLETE was set, no one is racing to observe INNER_COMPLETE;
        // if INNER_COMPLETE was not set, no need to preserve it.
        int a = active.getAndUpdate(o -> o | OUTER_COMPLETE);

        // OUTER_COMPLETE may have been set by inner onError, so check OUTER_COMPLETE
        // is not set.
        // the one who sets the second bit in ALL_COMPLETE must call super.complete
        // bit masking, because maybe also NOT_STARTED
        if ((a & ALL_COMPLETE) == INNER_COMPLETE) {
            super.complete();
        }
    }

    @Override
    public void request(long n) {
        while (!backp.maybeRequest(n)) {
            // race against Publisher setting a new backp
        }
    }

    @Override
    public void cancel() {
        backp.cancel();
        super.cancel();
    }

    static class Backpressure {
        private final AtomicLong requested = new AtomicLong(0);
        private final Flow.Subscription sub;

        private int bufferSize = Integer.parseInt(
                System.getProperty("helidon.common.reactive.flatMap.buffer.size", String.valueOf(DEFAULT_BUFFER_SIZE)));
        private BlockingQueue<U> buffer = new ArrayBlockingQueue<>(bufferSize);
        private InnerSubscriber<? super X> lastSubscriber = null;

        public boolean isComplete() {
            return Objects.isNull(lastSubscriber) || (lastSubscriber.isDone() && buffer.isEmpty());
        }

        boolean maybeRequest(long n) {
            if (n <= 0) {
                // let the Subscription deal with bad requests
                sub.request(n);
                return true;
            }

            long r;
            do {
                r = requested.get();
                if (r < 0) {
                    return false;
                }
            } while (!requested.compareAndSet(r, Long.MAX_VALUE - r > n ? r + n : Long.MAX_VALUE));
            sub.request(n);
            return true;
        }

        void deliver() {
            long r;
            do {
                r = requested.get();
            } while (r != Long.MAX_VALUE && !requested.compareAndSet(r, r - 1));
        }

        @SuppressWarnings("unchecked")
        public InnerSubscriber<? super X> executeMapper(U item) {
            InnerSubscriber<? super X> innerSubscriber = null;
            try {
                innerSubscriber = new InnerSubscriber<>();
                innerSubscriber.whenComplete(this::tryNext);
                mapper.apply((T) item).subscribe(innerSubscriber);
            } catch (Throwable t) {
                subscription.cancel();
                subscriber.onError(t);
            }
            return innerSubscriber;
        }
    }

    class Inner implements Flow.Subscriber<X> {

        @Override
        public void onSubscribe(Flow.Subscription sub) {
            var a = active.getAndUpdate(o -> o | INNER_SUBSCRIBED);
            if ((a & INNER_SUBSCRIBED) == INNER_SUBSCRIBED) {
                sub.cancel();
                return;
            }

            Backpressure old = backp;
            backp = new Backpressure(sub);
            long unused = old.terminate();

            if (unused == 0) {
                return;
            }

            request(unused);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void onNext(R o) {
            Objects.requireNonNull(o);
            MultiFlatMapProcessor.this.subscriber.onNext((X) o);
            //just counting leftovers
            requestCounter.tryDecrement();
        }

        @Override
        public void onError(Throwable th) {
            Objects.requireNonNull(th);
            // NOT_STARTED is clear.
            //
            // the one who sets the second bit in ALL_COMPLETE must call super.complete
            // set always succeeds to do this; should not wait for upstream to complete
            // i.e. it may be that active is either 0 or OUTER_COMPLETE;
            // MultiFlatMapProcessor.complete cannot enter super.complete, because it
            // cannot see INNER_COMPLETE set; and when it does, it will see both bits
            // are set.
            active.set(ALL_COMPLETE);
            MultiFlatMapProcessor.super.getSubscription().cancel();
            MultiFlatMapProcessor.super.complete(th);
        }

        @Override
        public void onComplete() {
            // NOT_STARTED is clear.
            //
            // if OUTER_COMPLETE is set, there will be no one to observe OUTER_COMPLETE;
            // if OUTER_COMPLETE is not set, not preserving OUTER_COMPLETE is ok.
            // So it is ok to getAndSet
            int a = active.getAndSet(INNER_COMPLETE);

            // the one who sets the second bit in ALL_COMPLETE must call super.complete
            if ((a & OUTER_COMPLETE) == OUTER_COMPLETE) {
                MultiFlatMapProcessor.this.complete();
                return;
            }

            MultiFlatMapProcessor.this.getSubscription().request(1);
        }
    }
}

