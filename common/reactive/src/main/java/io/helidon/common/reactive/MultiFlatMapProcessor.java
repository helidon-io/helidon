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
public class MultiFlatMapProcessor<T, X> extends BaseProcessor<T, X> implements Multi<X> {
    private static final int INNER_COMPLETE = 1;
    private static final int OUTER_COMPLETE = 2;
    private static final int NOT_STARTED = 4;
    private static final int INNER_SUBSCRIBED = 8;
    private static final int ALL_COMPLETE = INNER_COMPLETE | OUTER_COMPLETE;

    private Function<T, Flow.Publisher<X>> mapper;
    private final AtomicInteger active = new AtomicInteger(NOT_STARTED | INNER_COMPLETE);
    private final Inner inner = new Inner();

    private volatile Backpressure backp = new Backpressure(new Flow.Subscription() {
        @Override
        public void request(long n) {
            if (n <= 0) {
                // let the Subscription deal with bad requests
                getSubscription().request(n);
                return;
            }

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
     */
    protected MultiFlatMapProcessor(Function<T, Flow.Publisher<X>> mapper) {
        Objects.requireNonNull(mapper);
        this.mapper = mapper;
    }

    /**
     * Create new {@link MultiFlatMapProcessor} with item to {@link java.lang.Iterable} mapper.
     *
     * @param mapper to provide iterable for every item from upstream
     * @param <T>    input item type
     * @param <R>    output item type
     * @return {@link MultiFlatMapProcessor}
     */
    public static <T, R> MultiFlatMapProcessor<T, R> fromIterableMapper(Function<T, Iterable<R>> mapper) {
        return new MultiFlatMapProcessor<>(o -> Multi.from(mapper.apply(o)));
    }

    /**
     * Create new {@link MultiFlatMapProcessor} with item to {@link java.util.concurrent.Flow.Publisher} mapper.
     *
     * @param mapper to provide iterable for every item from upstream
     * @param <T>    input item type
     * @param <U>    output item type
     * @return {@link MultiFlatMapProcessor}
     */
    public static <T, U> MultiFlatMapProcessor<T, U> fromPublisherMapper(Function<T, Flow.Publisher<U>> mapper) {
        return new MultiFlatMapProcessor<>(mapper);
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
    protected void submit(T item) {
        // clear INNER_COMPLETE, if set; no other flags are set
        active.set(0);
        try {
            var mapperReturnedPublisher = mapper.apply(item);
            if (Objects.isNull(mapperReturnedPublisher)) {
                throw new IllegalStateException("Mapper returned a null value!");
            }
            mapperReturnedPublisher.subscribe(inner);
        } catch (Throwable t) {
            getSubscription().cancel();
            active.set(ALL_COMPLETE);
            getSubscriber().onError(t);
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
    @SuppressWarnings("checkstyle:emptyStatement")
    public void request(long n) {
        // race against Publisher setting a new backp
        while (!backp.maybeRequest(n)) ;
    }

    @Override
    public void cancel() {
        backp.cancel();
        super.cancel();
    }

    static class Backpressure {
        private final AtomicLong requested = new AtomicLong(0);
        private final Flow.Subscription sub;

        Backpressure(Flow.Subscription sub) {
            this.sub = sub;
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

        long terminate() {
            return requested.getAndSet(-1);
        }

        void cancel() {
            sub.cancel();
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
        public void onNext(X item) {
            Objects.requireNonNull(item);
            backp.deliver();
            MultiFlatMapProcessor.this.getSubscriber().onNext(item);
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

