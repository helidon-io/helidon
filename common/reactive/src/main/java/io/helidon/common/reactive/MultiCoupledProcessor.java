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

import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coupled processor sends items received to the passed in subscriber, and emits items received from the passed in publisher.
 * <pre>{@code
 *     +
 *     |  Inlet/upstream publisher
 * +-------+
 * |   |   |   passed in subscriber
 * |   +-------------------------->
 * |       |   passed in publisher
 * |   +--------------------------+
 * |   |   |
 * +-------+
 *     |  Outlet/downstream subscriber
 *     v
 * }</pre>
 *
 * @param <T> Inlet and passed in subscriber item type
 * @param <R> Outlet and passed in publisher item type
 */
public class MultiCoupledProcessor<T, R> implements Flow.Processor<T, R>, Multi<R> {

    private SequentialSubscriber<T> passedInSubscriber;
    private SequentialSubscriber<? super R> outletSubscriber;
    private Flow.Publisher<R> passedInPublisher;
    private Flow.Subscriber<? super T> inletSubscriber;
    private Flow.Subscription inletSubscription;
    private Flow.Subscription passedInPublisherSubscription;
    private AtomicBoolean cancelled = new AtomicBoolean(false);
    private AtomicBoolean done = new AtomicBoolean(false);
    private Queue<Runnable> forbiddenCalls = new LinkedList<>();
    private CompletableFuture<Void> readyToSignalPassedInSubscriber = new CompletableFuture<>();
    private CompletableFuture<Void> readyToSignalOutletSubscriber = new CompletableFuture<>();


    private MultiCoupledProcessor(Flow.Subscriber<T> passedInSubscriber, Flow.Publisher<R> passedInPublisher) {
        this.passedInSubscriber = SequentialSubscriber.create(passedInSubscriber);
        this.passedInPublisher = passedInPublisher;
        this.inletSubscriber = this;
    }

    /**
     * Create new {@link MultiCoupledProcessor}.
     *
     * @param passedInSubscriber to send items from inlet to
     * @param passedInPublisher  to get items for outlet from
     * @param <T>                Inlet and passed in subscriber item type
     * @param <R>                Outlet and passed in publisher item type
     * @return {@link MultiCoupledProcessor}
     */
    public static <T, R> MultiCoupledProcessor<T, R> create(Flow.Subscriber<T> passedInSubscriber,
                                                            Flow.Publisher<R> passedInPublisher) {
        return new MultiCoupledProcessor<>(passedInSubscriber, passedInPublisher);
    }

    @Override
    public void subscribe(Flow.Subscriber<? super R> outletSubscriber) {
        this.outletSubscriber = SequentialSubscriber.create(outletSubscriber);
        passedInPublisher.subscribe(new Flow.Subscriber<R>() {

            @Override
            public void onSubscribe(Flow.Subscription passedInPublisherSubscription) {
                //Passed in publisher called onSubscribe
                Objects.requireNonNull(passedInPublisherSubscription);
                // https://github.com/reactive-streams/reactive-streams-jvm#2.5
                if (Objects.nonNull(MultiCoupledProcessor.this.passedInPublisherSubscription) || cancelled.get()) {
                    passedInPublisherSubscription.cancel();
                    return;
                }
                MultiCoupledProcessor.this.passedInPublisherSubscription = passedInPublisherSubscription;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void onNext(R t) {
                //Passed in publisher sent onNext
                tryForbiddenCalls();
                if (done.get()) return;
                Objects.requireNonNull(t);
                outletSubscriber.onNext(t);
            }


            @Override
            public void onError(Throwable t) {
                //Passed in publisher sent onError
                done.set(true);
                Objects.requireNonNull(t);
                readyToSignalOutletSubscriber.whenComplete((aVoid, throwable) -> {
                    outletSubscriber.onError(t);
                });
                readyToSignalPassedInSubscriber.whenComplete((aVoid, throwable) -> {
                    //203 https://github.com/eclipse/microprofile-reactive-streams-operators/issues/131
                    forbiddenCalls.offer(() -> Optional.ofNullable(inletSubscription).ifPresent(Flow.Subscription::cancel));
                    passedInSubscriber.onError(t);
                });
                inletSubscriber.onError(t);
            }

            @Override
            public void onComplete() {
                //Passed in publisher completed
                done.set(true);
                outletSubscriber.onComplete();
                readyToSignalPassedInSubscriber.whenComplete((aVoid, throwable) -> {
                    //203 https://github.com/eclipse/microprofile-reactive-streams-operators/issues/131
                    forbiddenCalls.offer(() -> Optional.ofNullable(inletSubscription).ifPresent(Flow.Subscription::cancel));
                    passedInSubscriber.onComplete();
                });
            }
        });

        outletSubscriber.onSubscribe(new Flow.Subscription() {

            @Override
            public void request(long n) {
                // Request from outlet subscriber
                passedInPublisherSubscription.request(n);
                tryForbiddenCalls();
            }

            @Override
            public void cancel() {
                // Cancel from outlet subscriber
                passedInSubscriber.onComplete();
                Optional.ofNullable(inletSubscription).ifPresent(Flow.Subscription::cancel);
                Optional.ofNullable(passedInPublisherSubscription).ifPresent(Flow.Subscription::cancel);
                tryForbiddenCalls();
            }
        });
        readyToSignalOutletSubscriber.complete(null);
    }

    @Override
    public void onSubscribe(Flow.Subscription inletSubscription) {
        Objects.requireNonNull(inletSubscription);
        // https://github.com/reactive-streams/reactive-streams-jvm#2.5
        if (Objects.nonNull(this.inletSubscription) || cancelled.get()) {
            inletSubscription.cancel();
            return;
        }
        this.inletSubscription = inletSubscription;
        passedInSubscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
                inletSubscription.request(n);
                tryForbiddenCalls();
            }

            @Override
            public void cancel() {
                // Cancel from passed in subscriber
                if (cancelled.getAndSet(true)) {
                    return;
                }
                inletSubscription.cancel();
                outletSubscriber.onComplete();
                passedInPublisherSubscription.cancel();
                tryForbiddenCalls();
            }
        });

        readyToSignalPassedInSubscriber.complete(null);
        tryForbiddenCalls();
    }

    @Override
    public void onNext(T t) {
        // Inlet/upstream publisher sent onNext
        if (done.get()) return;
        passedInSubscriber.onNext(Objects.requireNonNull(t));
    }

    @Override
    public void onError(Throwable t) {
        // Inlet/upstream publisher sent error
        done.set(true);
        Objects.requireNonNull(t);
        readyToSignalPassedInSubscriber.whenComplete((aVoid, throwable) -> {
            passedInSubscriber.onError(t);
        });
        readyToSignalOutletSubscriber.whenComplete((aVoid, throwable) -> {
            outletSubscriber.onError(t);
        });
        forbiddenCalls.add(() -> passedInPublisherSubscription.cancel());
    }

    @Override
    public void onComplete() {
        // Inlet/upstream publisher completed
        done.set(true);
        readyToSignalPassedInSubscriber.whenComplete((aVoid, throwable) -> {
            passedInSubscriber.onComplete();
        });
        outletSubscriber.onComplete();
        forbiddenCalls.add(() -> passedInPublisherSubscription.cancel());
    }

    private void tryForbiddenCalls() {
        while (true) {
            Runnable polledCall = forbiddenCalls.poll();
            if (Objects.isNull(polledCall)) {
                return;
            }
            polledCall.run();
        }
    }

}
