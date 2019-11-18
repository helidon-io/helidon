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

import io.helidon.microprofile.messaging.MessageUtils;
import io.helidon.microprofile.messaging.channel.AbstractChannel;
import io.helidon.microprofile.messaging.channel.ProcessorMethod;
import org.eclipse.microprofile.reactive.streams.operators.ProcessorBuilder;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import javax.enterprise.inject.spi.DeploymentException;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ExecutionException;

/**
 * Passes publisher to processor method ex:
 * <pre>{@code
 *     @Incoming("inner-processor")
 *     @Outgoing("inner-processor-2")
 *     public PublisherBuilder<String> process(PublisherBuilder<String> msg) {
 *         return msg;
 *     }
 * }</pre>
 */
public class ProxyProcessor implements Processor<Object, Object> {

    private final ProcessorMethod processorMethod;
    private final Publisher<Object> publisher;
    private Subscriber subscriber;
    private Processor<Object, Object> processor;
    private boolean subscribed = false;

    @SuppressWarnings("unchecked")
    public ProxyProcessor(ProcessorMethod processorMethod) {
        this.processorMethod = processorMethod;
        try {
            if (processorMethod.getType() == AbstractChannel.Type.PROCESSOR_PUBLISHER_BUILDER_2_PUBLISHER_BUILDER) {
                PublisherBuilder<Object> paramPublisherBuilder = ReactiveStreams.fromPublisher(this);
                publisher = ((PublisherBuilder<Object>) processorMethod
                        .getMethod()
                        .invoke(processorMethod.getBeanInstance(), paramPublisherBuilder)).buildRs();

            } else if (processorMethod.getType() == AbstractChannel.Type.PROCESSOR_PUBLISHER_2_PUBLISHER) {
                publisher = ((Publisher<Object>) processorMethod
                        .getMethod()
                        .invoke(processorMethod.getBeanInstance(), this));

            } else if (processorMethod.getType() == AbstractChannel.Type.PROCESSOR_VOID_2_PROCESSOR_BUILDER) {
                processor = ((ProcessorBuilder<Object, Object>) processorMethod
                        .getMethod()
                        .invoke(processorMethod.getBeanInstance())).buildRs();
                publisher = processor;

            } else if (processorMethod.getType() == AbstractChannel.Type.PROCESSOR_VOID_2_PROCESSOR) {
                processor = ((Processor<Object, Object>) processorMethod
                        .getMethod()
                        .invoke(processorMethod.getBeanInstance()));
                publisher = processor;

            } else {
                throw new UnsupportedOperationException("Unknown signature type " + processorMethod.getType());
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new DeploymentException(e);
        }

    }

    @Override
    public void subscribe(Subscriber s) {
        if (processor != null) {
            // Backed by real  processor
            processor.subscribe(s);
            subscriber = processor;
        } else if (!subscribed && publisher != null) {
            // Backed by publisher
            subscribed = true;
            publisher.subscribe(s);
        } else {
            subscriber = s;
        }
    }

    @Override
    public void onSubscribe(Subscription s) {
        subscriber.onSubscribe(s);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onNext(Object o) {
        try {
            subscriber.onNext(MessageUtils.unwrap(o, this.processorMethod.getMethod()));
        } catch (ExecutionException | InterruptedException e) {
            onError(e);
        }
    }

    @Override
    public void onError(Throwable t) {
        subscriber.onError(t);
    }

    @Override
    public void onComplete() {
        subscriber.onComplete();
    }
}
