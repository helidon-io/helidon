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

import java.util.LinkedList;
import java.util.List;

import io.helidon.common.reactive.Flow;
import io.helidon.microprofile.reactive.hybrid.HybridProcessor;

import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * {@link org.reactivestreams.Processor} wrapping ordered list of {@link org.reactivestreams.Processor}s.
 */
public class CumulativeProcessor implements Processor<Object, Object> {
    private LinkedList<Processor<Object, Object>> processorList = new LinkedList<>();

    /**
     * Create {@link org.reactivestreams.Processor} wrapping ordered list of {@link io.helidon.common.reactive.Flow.Processor}s.
     *
     * @param precedingProcessorList ordered list of {@link io.helidon.common.reactive.Flow.Processor}s
     */
    CumulativeProcessor(List<Flow.Processor<Object, Object>> precedingProcessorList) {
        //preceding processors
        precedingProcessorList.forEach(fp -> this.processorList.add(HybridProcessor.from(fp)));
        //pass-thru if no processors provided
        this.processorList.add(HybridProcessor.from(TappedProcessor.create()));
    }

    @Override
    public void subscribe(Subscriber<? super Object> s) {
        processorList.getLast().subscribe(s);
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        // This is the time for connecting all processors
        Processor<Object, Object> lastProcessor = null;
        for (Processor<Object, Object> processor : processorList) {
            if (lastProcessor != null) {
                lastProcessor.subscribe(processor);
            }
            lastProcessor = processor;
        }
        processorList.getFirst().onSubscribe(subscription);
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
