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

package io.helidon.microprofile.messaging.channel;

import io.helidon.config.Config;
import io.helidon.microprofile.messaging.reactive.InternalProcessor;
import io.helidon.microprofile.messaging.reactive.ProxyProcessor;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.ProcessorBuilder;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;

import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.DeploymentException;

import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

public class ProcessorMethod extends AbstractChannel {

    private static final Logger LOGGER = Logger.getLogger(ProcessorMethod.class.getName());

    private Processor<Object, Object> processor;
    private UniversalChannel incomingChannel;
    private UniversalChannel outgoingChannel;

    public ProcessorMethod(AnnotatedMethod method, ChannelRouter router) {
        super(method.getJavaMember(), router);
        super.incomingChannelName =
                method.getAnnotation(Incoming.class).value();
        super.outgoingChannelName =
                method.getAnnotation(Outgoing.class).value();
        resolveSignatureType();
    }

    @Override
    public void init(BeanManager beanManager, Config config) {
        super.init(beanManager, config);
        if (type.isInvokeAtAssembly()) {
            processor = new ProxyProcessor(this);
        } else {
            // Create brand new subscriber
            processor = new InternalProcessor(this);
        }
    }

    @Override
    public void validate() {
        if (incomingChannelName == null || incomingChannelName.trim().isEmpty()) {
            throw new DeploymentException("Missing channel name in annotation @Incoming on method "
                    + method.toString());
        }
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

    public Processor<Object, Object> getProcessor() {
        return processor;
    }

    public UniversalChannel getIncomingChannel() {
        return incomingChannel;
    }

    public void setIncomingChannel(UniversalChannel incomingChannel) {
        this.incomingChannel = incomingChannel;
    }

    public UniversalChannel getOutgoingChannel() {
        return outgoingChannel;
    }

    public void setOutgoingChannel(UniversalChannel outgoingChannel) {
        this.outgoingChannel = outgoingChannel;
    }

    private void resolveSignatureType() {
        Class<?> returnType = this.method.getReturnType();
            Class<?> parameterType = Void.TYPE;
        if (this.method.getParameterTypes().length == 1) {
            parameterType = this.method.getParameterTypes()[0];
        } else if (this.method.getParameterTypes().length > 1) {
            throw new DeploymentException("Bad processor method signature " + method);
        }
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
