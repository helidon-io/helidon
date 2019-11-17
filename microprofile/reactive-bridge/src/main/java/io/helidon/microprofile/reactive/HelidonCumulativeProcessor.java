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

import io.helidon.common.reactive.Flow;
import io.helidon.microprofile.reactive.hybrid.HybridProcessor;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class HelidonCumulativeProcessor implements Processor<Object, Object> {
    private LinkedList<Processor<Object, Object>> processorList = new LinkedList<>();
    private Processor<Object, Object> subscriber;

    public HelidonCumulativeProcessor(List<Flow.Processor<Object, Object>> precedingProcessorList) {
        //preceding processors
        precedingProcessorList.forEach(fp -> this.processorList.add(HybridProcessor.from(fp)));
    }

    @Override
    public void subscribe(Subscriber<? super Object> s) {
        processorList.getLast().subscribe(s);
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        // This is the time for connecting all processors
        Processor<Object, Object> lastProcessor = null;
        for (Iterator<Processor<Object, Object>> it = processorList.iterator(); it.hasNext(); ) {
            Processor<Object, Object> processor = it.next();
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
