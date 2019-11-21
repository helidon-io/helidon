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

import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;

import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.DeploymentException;

import io.helidon.config.Config;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.ProcessorBuilder;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;

class ProcessorMethod extends AbstractMethod {

    private Processor<Object, Object> processor;
    private UniversalChannel outgoingChannel;

    ProcessorMethod(AnnotatedMethod method) {
        super(method.getJavaMember());
        super.setIncomingChannelName(method.getAnnotation(Incoming.class).value());
        super.setOutgoingChannelName(method.getAnnotation(Outgoing.class).value());
    }

    @Override
    public void init(BeanManager beanManager, Config config) {
        super.init(beanManager, config);
        if (getType().isInvokeAtAssembly()) {
            processor = new ProxyProcessor(this);
        } else {
            // Create brand new subscriber
            processor = new InternalProcessor(this);
        }
    }

    @Override
    public void validate() {
        if (getIncomingChannelName() == null || getIncomingChannelName().trim().isEmpty()) {
            throw new DeploymentException(String
                    .format("Missing channel name in annotation @Incoming on method %s", getMethod()));
        }
        if (getOutgoingChannelName() == null || getOutgoingChannelName().trim().isEmpty()) {
            throw new DeploymentException(String
                    .format("Missing channel name in annotation @Outgoing on method %s", getMethod()));
        }
        if (this.getMethod().getParameterTypes().length > 1) {
            throw new DeploymentException("Bad processor method signature, "
                    + "wrong number of parameters, only one or none allowed."
                    + getMethod());
        }
    }

    public Processor<Object, Object> getProcessor() {
        return processor;
    }

    UniversalChannel getOutgoingChannel() {
        return outgoingChannel;
    }

    void setOutgoingChannel(UniversalChannel outgoingChannel) {
        this.outgoingChannel = outgoingChannel;
    }

    @Override
    protected void resolveSignatureType() {
        Method method = getMethod();
        Class<?> returnType = method.getReturnType();
        Class<?> parameterType = Void.TYPE;

        if (method.getParameterTypes().length == 1) {
            parameterType = method.getParameterTypes()[0];

        } else if (method.getParameterTypes().length > 1) {
            throw new DeploymentException(String
                    .format("Bad processor method signature %s", method));
        }
        if (Void.TYPE.equals(parameterType)) {
            if (Processor.class.equals(returnType)) {
                setType(MethodSignatureType.PROCESSOR_PROCESSOR_MSG_2_VOID);

            } else if (ProcessorBuilder.class.equals(returnType)) {
                setType(MethodSignatureType.PROCESSOR_PROCESSOR_BUILDER_MSG_2_VOID);

            } else {
                throw new DeploymentException(String
                        .format("Bad processor method signature %s", method));
            }
        } else if (Publisher.class.equals(parameterType) && Publisher.class.equals(returnType)) {
            setType(MethodSignatureType.PROCESSOR_PUBLISHER_2_PUBLISHER);

        } else if (PublisherBuilder.class.equals(parameterType) && PublisherBuilder.class.equals(returnType)) {
            setType(MethodSignatureType.PROCESSOR_PUBLISHER_BUILDER_2_PUBLISHER_BUILDER);

        } else {
            if (Publisher.class.equals(returnType)) {
                setType(MethodSignatureType.PROCESSOR_PUBLISHER_MSG_2_MSG);

            } else if (CompletionStage.class.equals(returnType)) {
                setType(MethodSignatureType.PROCESSOR_COMPL_STAGE_MSG_2_MSG);

            } else {
                setType(MethodSignatureType.PROCESSOR_MSG_2_MSG);
            }
        }
    }

}
