/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
 */

package io.helidon.microprofile.messaging;

import io.helidon.config.Config;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.ProcessorBuilder;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;

import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.DeploymentException;

import java.util.List;
import java.util.concurrent.CompletionStage;

public class ProcessorChannelMethod extends IncomingChannelMethod {

    private SubscriberBuilder<? extends Message<?>, Void> subscriberBuilder;
    private Processor<Object, Object> processor;
    private Publisher<Object> publisher;

    public ProcessorChannelMethod(AnnotatedMethod method, ChannelRouter router) {
        super(method, router);
        super.outgoingChannelName =
                method.getAnnotation(Outgoing.class).value();
        resolveSignatureType();
    }

    @Override
    public void init(BeanManager beanManager, Config config) {
        super.init(beanManager, config);
        if (type.isInvokeAtAssembly()) {
            // TODO: Incoming methods returning custom processor
            throw new UnsupportedOperationException("Not implemented yet!");
        } else {
            // Create brand new subscriber
            processor = new InternalProcessor(this);
        }
    }

    @Override
    void validate() {
        super.validate();
        if (outgoingChannelName == null || outgoingChannelName.trim().isEmpty()) {
            throw new DeploymentException("Missing channel name in annotation @Outgoing on method "
                    + method.toString());
        }
        if (this.method.getParameterTypes().length > 1) {
            throw new DeploymentException("Bad processor method signature, "
                    + "wrong number of parameters, only one or none allowed."
                    + method.toString());
        }
    }

    @Override
    protected void connect() {
        // Connect to Incoming methods with publisher
        List<IncomingChannelMethod> incomingChannelMethods = getRouter().getIncomingSubscribers(getOutgoingChannelName());
        if (incomingChannelMethods != null) {
            for (IncomingChannelMethod s : getRouter().getIncomingSubscribers(getOutgoingChannelName())) {
                System.out.println("Connecting " + this.getOutgoingChannelName() + " " + this.method.getName() + " to " + s.method.getName());
                connected = true;
                s.connected = true;
                processor.subscribe(s.getSubscriber());
                publisher.subscribe(processor);
            }
        }

    }

    public void setPublisher(Publisher<Object> publisher) {
        this.publisher = publisher;
    }

    protected void resolveSignatureType() {
        Class<?> returnType = this.method.getReturnType();
        Class<?> parameterType = this.method.getParameterTypes()[0];
        if (Void.TYPE.equals(parameterType)) {
            if (Processor.class.equals(returnType)) {
                this.type = Type.PROCESSOR_VOID_2_PROCESSOR;
            } else if (ProcessorBuilder.class.equals(returnType)) {
                this.type = Type.PROCESSOR_VOID_2_PROCESSOR_BUILDER;
            } else {
                throw new DeploymentException("Bad processor method signature " + method);
            }
        } else if (Publisher.class.equals(parameterType) && Publisher.class.equals(returnType)) {
            this.type = Type.PROCESSOR_PUBLISHER_2_PUBLISHER;
        } else if (PublisherBuilder.class.equals(parameterType) && PublisherBuilder.class.equals(returnType)) {
            this.type = Type.PROCESSOR_PUBLISHER_BUILDER_2_PUBLISHER_BUILDER;
        } else {
            if (Publisher.class.equals(returnType)) {
                this.type = Type.PROCESSOR_MSG_2_PUBLISHER;
            } else if (CompletionStage.class.equals(returnType)) {
                this.type = Type.PROCESSOR_MSG_2_COMPL_STAGE;
            } else {
                this.type = Type.PROCESSOR_MSG_2_MSG;
            }
        }
    }

}
