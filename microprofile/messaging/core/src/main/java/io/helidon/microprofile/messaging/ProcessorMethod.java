/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;

import io.helidon.common.Errors;
import io.helidon.config.Config;

import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.BeanManager;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.reactivestreams.Processor;

class ProcessorMethod extends AbstractMessagingMethod {

    private Processor<Object, Object> processor;
    private UniversalChannel outgoingChannel;

    ProcessorMethod(AnnotatedMethod<?> method, Errors.Collector errors) {
        super(method.getJavaMember(), errors);
        super.setIncomingChannelName(method.getAnnotation(Incoming.class).value());
        super.setOutgoingChannelName(method.getAnnotation(Outgoing.class).value());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(BeanManager beanManager, Config config) {
        super.init(beanManager, config);
        if (getType().isInvokeAtAssembly()) {
            processor = new ProxyProcessor(this);
        } else {
            switch (getType()) {
                case PROCESSOR_PUBLISHER_MSG_2_MSG:
                    processor = ReactiveStreams.builder()
                            .flatMap(in -> ReactiveStreams.<Message<?>>fromPublisher(invoke(in))
                                    .map(out -> postProcess(in, out)))
                            .buildRs();
                    break;
                case PROCESSOR_PUBLISHER_PAYL_2_PAYL:
                    processor = ReactiveStreams.builder()
                            .flatMap(in -> ReactiveStreams.fromPublisher(invoke(in))
                                    .map(out -> postProcess(in, out)))
                            .buildRs();
                    break;
                case PROCESSOR_PUBLISHER_BUILDER_MSG_2_MSG:
                case PROCESSOR_PUBLISHER_BUILDER_PAYL_2_PAYL:
                    processor = ReactiveStreams.builder()
                            .flatMap(in -> ((PublisherBuilder<Object>) invoke(in))
                                    .map(out -> postProcess(in, out)))
                            .buildRs();
                    break;
                case PROCESSOR_MSG_2_MSG:
                case PROCESSOR_PAYL_2_PAYL:
                    processor = ReactiveStreams.builder()
                            .map(in -> postProcess(in, (invoke(in))))
                            .buildRs();
                    break;
                case PROCESSOR_COMPL_STAGE_MSG_2_MSG:
                case PROCESSOR_COMPL_STAGE_PAYL_2_PAYL:
                    processor = ReactiveStreams.builder()
                            .flatMap(in -> ReactiveStreams.fromCompletionStageNullable((CompletionStage<?>) invoke(in))
                                    .map(out -> postProcess(in, out)))
                            .buildRs();
                    break;
                default:
                    throw new MessagingDeploymentException("Invalid messaging method signature " + getMethod());
            }
        }
    }

    @SuppressWarnings("unchecked")
    <R> R invoke(Object incomingValue){
        Method method = getMethod();
        //Params size is already validated by ProcessorMethod
        Class<?> paramType = method.getParameterTypes()[0];
        try {
            return (R) method.invoke(getBeanInstance(), preProcess(incomingValue, paramType));
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void validate() {
        super.validate();

        if (getIncomingChannelName() == null || getIncomingChannelName().trim().isEmpty()) {
            super.errors().fatal(String.format("Missing channel name in annotation @Incoming on method %s", getMethod()));
        }
        if (getOutgoingChannelName() == null || getOutgoingChannelName().trim().isEmpty()) {
            super.errors().fatal(String.format("Missing channel name in annotation @Outgoing on method %s", getMethod()));
        }
        if (this.getMethod().getParameterTypes().length > 1) {
            super.errors()
                    .fatal(String
                            .format("Bad processor method signature, wrong number of parameters, only one or none allowed.%s",
                                    getMethod()));
        }
    }

    private Object preProcess(final Object incomingValue, final Class<?> expectedParamType) {
        if (getAckStrategy().equals(Acknowledgment.Strategy.PRE_PROCESSING)
                && incomingValue instanceof Message) {
            Message<?> incomingMessage = (Message<?>) incomingValue;
            incomingMessage.ack();
        }

        return MessageUtils.unwrap(incomingValue, expectedParamType);
    }

    private Object postProcess(final Object incomingValue, final Object outgoingValue) {
        Message<?> wrappedOutgoing = (Message<?>) MessageUtils.unwrap(outgoingValue, Message.class);
        if (getAckStrategy().equals(Acknowledgment.Strategy.POST_PROCESSING)) {
            Message<?> wrappedIncoming = (Message<?>) MessageUtils.unwrap(incomingValue, Message.class);
            wrappedOutgoing = (Message<?>) MessageUtils.unwrap(outgoingValue, Message.class, wrappedIncoming::ack);
        }
        return wrappedOutgoing;
    }


    Processor<Object, Object> getProcessor() {
        return processor;
    }

    UniversalChannel getOutgoingChannel() {
        return outgoingChannel;
    }

    void setOutgoingChannel(UniversalChannel outgoingChannel) {
        this.outgoingChannel = outgoingChannel;
    }

}
