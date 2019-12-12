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

package io.helidon.microprofile.reactive;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class FlatMapCompletionStageProcessor implements Processor<Object, Object> {

    private Subscription upstreamSubscription;
    private Optional<Subscriber<? super Object>> downStreamSubscriber = Optional.empty();
    private CSBuffer<Object> buffer;
    private AtomicBoolean onCompleteReceivedAlready = new AtomicBoolean(false);

    @SuppressWarnings("unchecked")
    public FlatMapCompletionStageProcessor(Function<?, CompletionStage<?>> mapper) {
        Function<Object, CompletionStage<?>> csMapper = (Function<Object, CompletionStage<?>>) mapper;
        buffer = new CSBuffer<>(csMapper);
    }

    @Override
    public void subscribe(Subscriber<? super Object> subscriber) {
        downStreamSubscriber = Optional.of(subscriber);
        if (Objects.nonNull(this.upstreamSubscription)) {
            subscriber.onSubscribe(new InnerSubscription());
        }
    }

    @Override
    public void onSubscribe(Subscription upstreamSubscription) {
        if (Objects.nonNull(this.upstreamSubscription)) {
            upstreamSubscription.cancel();
            return;
        }
        this.upstreamSubscription = upstreamSubscription;
        downStreamSubscriber.ifPresent(s -> s.onSubscribe(new InnerSubscription()));
    }

    private class InnerSubscription implements Subscription {
        @Override
        public void request(long n) {
            upstreamSubscription.request(n);
        }

        @Override
        public void cancel() {
            upstreamSubscription.cancel();
        }
    }

    @Override
    public void onNext(Object o) {
        Objects.requireNonNull(o);
        buffer.offer(o);
    }

    @Override
    public void onError(Throwable t) {
        Objects.requireNonNull(t);
        downStreamSubscriber.get().onError(t);
    }

    @Override
    public void onComplete() {
        onCompleteReceivedAlready.set(true);
        if (buffer.isComplete()) {
            //Have to wait for all CS to be finished
            downStreamSubscriber.get().onComplete();
        }
    }

    private class CSBuffer<U> {

        private BlockingQueue<Object> buffer = new ArrayBlockingQueue<>(64);
        private Function<Object, CompletionStage<Object>> mapper;
        private CompletableFuture<Object> lastCs = null;
        private ReentrantLock bufferLock = new ReentrantLock();

        @SuppressWarnings("unchecked")
        public CSBuffer(Function<Object, CompletionStage<?>> mapper) {
            this.mapper = o -> (CompletionStage<Object>) mapper.apply(o);
        }

        public boolean isComplete() {
            return Objects.isNull(lastCs) || (lastCs.isDone() && buffer.isEmpty());
        }

        @SuppressWarnings("unchecked")
        public void tryNext(Object o, Throwable t) {
            bufferLock.lock();
            if (Objects.nonNull(t)) {
                upstreamSubscription.cancel();
                downStreamSubscriber.get().onError(t);
            }

            if (Objects.isNull(o)) {
                upstreamSubscription.cancel();
                downStreamSubscriber.get().onError(new NullPointerException());
            }
            downStreamSubscriber.get().onNext((U) o);
            Object nextItem = buffer.poll();
            if (Objects.nonNull(nextItem)) {
                lastCs = executeMapper(nextItem);
                lastCs.whenComplete(this::tryNext);
            } else if (onCompleteReceivedAlready.get()) {
                // Received onComplete and all CS are done
                downStreamSubscriber.get().onComplete();
            }
            bufferLock.unlock();
        }

        public void offer(Object o) {
            bufferLock.lock();
            if (buffer.isEmpty() && (Objects.isNull(lastCs) || lastCs.isDone())) {
                lastCs = executeMapper(o);
                lastCs.whenComplete(this::tryNext);
            } else {
                buffer.offer(o);
            }
            bufferLock.unlock();
        }

        public CompletableFuture<Object> executeMapper(Object item) {
            CompletableFuture<Object> cs;
            try {
                cs = mapper.apply(item).toCompletableFuture();
            } catch (Throwable t) {
                upstreamSubscription.cancel();
                downStreamSubscriber.get().onError(t);
                //TODO: CompletableFuture.failedFuture since Java 9
                cs = new CompletableFuture<>();
                cs.completeExceptionally(t);
            }
            return cs;
        }
    }
}
