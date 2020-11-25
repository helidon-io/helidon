/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.messaging;

import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.streams.operators.ProcessorBuilder;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Passes publisher to processor method. ex:
 * <pre>{@code
 *     @Incoming("inner-processor")
 *     @Outgoing("inner-processor-2")
 *     public PublisherBuilder<String> process(PublisherBuilder<String> msg) {
 *         return msg;
 *     }
 * }</pre>
 */
class ProxyProcessor implements Processor<Object, Object> {

    private final ProcessorMethod processorMethod;
    private final Publisher<Object> publisher;
    private Subscriber<? super Object> subscriber;
    private Processor<Object, Object> processor;
    private boolean subscribed = false;

    @SuppressWarnings("unchecked")
    ProxyProcessor(ProcessorMethod processorMethod) {
        this.processorMethod = processorMethod;

        switch (processorMethod.getType()) {
            case PROCESSOR_PUBLISHER_BUILDER_MSG_2_PUBLISHER_BUILDER_MSG:
            case PROCESSOR_PUBLISHER_BUILDER_PAYL_2_PUBLISHER_BUILDER_PAYL:
                PublisherBuilder<Object> paramPublisherBuilder = ReactiveStreams.fromPublisher(this);
                publisher = ((PublisherBuilder<Object>) processorMethod
                        .invoke(paramPublisherBuilder)).buildRs();
                break;
            case PROCESSOR_PUBLISHER_MSG_2_PUBLISHER_MSG:
            case PROCESSOR_PUBLISHER_PAYL_2_PUBLISHER_PAYL:
                publisher = processorMethod.invoke(this);
                break;
            case PROCESSOR_PROCESSOR_BUILDER_MSG_2_VOID:
                processor = ((ProcessorBuilder<Object, Object>) processorMethod
                        .invoke()).buildRs();
                publisher = processor;
                break;
            case PROCESSOR_PROCESSOR_BUILDER_PAYL_2_VOID:
                processor = ReactiveStreams.builder()
                        .map(MessageUtils::unwrap)
                        .via((ProcessorBuilder<Object, Object>) processorMethod.invoke())
                        .map(MessageUtils::wrap)
                        .buildRs();
                publisher = processor;
                break;
            case PROCESSOR_PROCESSOR_MSG_2_VOID:
                processor = processorMethod.invoke();
                publisher = processor;
                break;
            case PROCESSOR_PROCESSOR_PAYL_2_VOID:
                processor = ReactiveStreams.builder()
                        .map(MessageUtils::unwrap)
                        .via((Processor<Object, Object>) processorMethod.invoke())
                        .map(MessageUtils::wrap)
                        .buildRs();
                publisher = processor;
                break;
            default:
                throw new UnsupportedOperationException("Unknown signature type " + processorMethod.getType());
        }

    }

    @Override
    public void subscribe(Subscriber<? super Object> s) {
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
    public void onNext(Object o) {
        preProcess(o);
        subscriber.onNext(MessageUtils.unwrap(o, this.processorMethod.getMethod()));
    }

    @Override
    public void onError(Throwable t) {
        subscriber.onError(t);
    }

    @Override
    public void onComplete() {
        subscriber.onComplete();
    }

    private void preProcess(Object incomingValue) {
        if (processorMethod.getAckStrategy().equals(Acknowledgment.Strategy.PRE_PROCESSING)
                && incomingValue instanceof Message) {
            Message<?> incomingMessage = (Message<?>) incomingValue;
            incomingMessage.ack();
        }
    }
}
