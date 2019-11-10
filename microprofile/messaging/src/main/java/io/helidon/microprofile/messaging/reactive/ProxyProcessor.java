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

package io.helidon.microprofile.messaging.reactive;

import io.helidon.microprofile.messaging.channel.AbstractChannel;
import io.helidon.microprofile.messaging.channel.ProcessorMethod;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.lang.reflect.InvocationTargetException;

public class ProxyProcessor implements Processor<Object, Object> {

    private final ProcessorMethod processorMethod;
    private final Publisher<Object> publisher;

    public ProxyProcessor(ProcessorMethod processorMethod) {
        this.processorMethod = processorMethod;
        try {
            if (processorMethod.getType() == AbstractChannel.Type.PROCESSOR_PUBLISHER_BUILDER_2_PUBLISHER_BUILDER) {
                PublisherBuilder<Object> paramPublisherBuilder = ReactiveStreams.fromPublisher(this);
                publisher = ((PublisherBuilder<Object>) processorMethod
                        .getMethod()
                        .invoke(processorMethod.getBeanInstance(), paramPublisherBuilder)).buildRs();
            } else {
                throw new UnsupportedOperationException("Not implemented yet!");
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void subscribe(Subscriber s) {
        publisher.subscribe(s);
    }

    @Override
    public void onSubscribe(Subscription s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onNext(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onError(Throwable t) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onComplete() {
        throw new UnsupportedOperationException();
    }
}
