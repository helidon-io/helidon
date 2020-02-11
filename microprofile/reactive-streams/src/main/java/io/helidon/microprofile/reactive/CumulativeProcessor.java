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

package io.helidon.microprofile.reactive;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.reactive.MultiTappedProcessor;
import io.helidon.common.reactive.SequentialSubscriber;
import io.helidon.microprofile.reactive.hybrid.HybridProcessor;

import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * {@link org.reactivestreams.Processor} wrapping ordered list of {@link org.reactivestreams.Processor}s.
 */
class CumulativeProcessor implements Processor<Object, Object> {
    private LinkedList<Processor<Object, Object>> processorList = new LinkedList<>();
    private Subscription subscription;
    private AtomicBoolean chainConnected = new AtomicBoolean(false);

    /**
     * Create {@link org.reactivestreams.Processor} wrapping ordered list of {@link java.util.concurrent.Flow.Processor}s.
     *
     * @param precedingProcessorList ordered list of {@link java.util.concurrent.Flow.Processor}s
     */
    CumulativeProcessor(List<Flow.Processor<Object, Object>> precedingProcessorList) {
        this.processorList.add(HybridProcessor.from(MultiTappedProcessor.create()
                .whenSubscribe(SequentialSubscriber::create)
        ));
        precedingProcessorList.forEach(fp -> this.processorList.add(HybridProcessor.from(fp)));
        this.processorList.add(HybridProcessor.from(MultiTappedProcessor.create()
                .whenSubscribe(SequentialSubscriber::create)
        ));
    }

    @Override
    public void subscribe(Subscriber<? super Object> s) {
        processorList.getLast().subscribe(s);
        tryChainSubscribe();
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        Objects.requireNonNull(subscription);
        if (Objects.nonNull(this.subscription)) {
            subscription.cancel();
            return;
        }
        this.subscription = subscription;
        tryChainSubscribe();
        processorList.getFirst().onSubscribe(subscription);
    }

    private void tryChainSubscribe() {
        if (!chainConnected.getAndSet(true)) {
            // This is the time for connecting all processors
            Processor<Object, Object> lastProcessor = null;
            for (Processor<Object, Object> processor : processorList) {
                if (lastProcessor != null) {
                    lastProcessor.subscribe(processor);
                }
                lastProcessor = processor;
            }
        }
    }

    @Override
    public void onNext(Object o) {
        processorList.getFirst().onNext(o);
    }

    @Override
    public void onError(Throwable t) {
        processorList.getFirst().onError(t);
    }

    @Override
    public void onComplete() {
        processorList.getFirst().onComplete();
    }
}
