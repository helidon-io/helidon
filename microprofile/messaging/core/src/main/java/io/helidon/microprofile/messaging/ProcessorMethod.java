/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import io.helidon.common.Errors;
import io.helidon.common.LazyValue;
import io.helidon.config.Config;

import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.BeanManager;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

class ProcessorMethod extends AbstractMessagingMethod implements OutgoingMember, IncomingMember {

    private static final System.Logger LOGGER = System.getLogger(ProcessorMethod.class.getName());

    private final LazyValue<Boolean> compatibilityMode = LazyValue.create(() ->
            ConfigProvider.getConfig().getOptionalValue("mp.messaging.helidon.propagate-errors", Boolean.class)
                    .orElse(false)
    );
    private Processor<Object, Object> processor;
    private UniversalChannel outgoingChannel;

    ProcessorMethod(AnnotatedMethod<?> method, Errors.Collector errors) {
        super(method.getJavaMember(), errors);
        super.setIncomingChannelName(method.getAnnotation(Incoming.class).value());
        super.setOutgoingChannelName(method.getAnnotation(Outgoing.class).value());
    }

    @Override
    public void init(BeanManager beanManager, Config config) {
        super.init(beanManager, config);
        if (getType().isInvokeAtAssembly()) {
            processor = new ProxyProcessor(this);
        } else {
            switch (getType()) {
                case PROCESSOR_PUBLISHER_MSG_2_MSG:
                    processor = invokeProcessor(msg ->
                            ReactiveStreams.fromPublisher(invoke(msg)));
                    break;
                case PROCESSOR_FLOW_PUBLISHER_MSG_2_MSG:
                    processor = invokeProcessor(msg ->
                            ReactiveStreams.fromPublisher(FlowAdapters.toPublisher(invoke(msg))));
                    break;
                case PROCESSOR_PUBLISHER_PAYL_2_PAYL:
                    processor = invokeProcessor(msg ->
                            ReactiveStreams.fromPublisher(invoke(msg.getPayload())));
                    break;
                case PROCESSOR_FLOW_PUBLISHER_PAYL_2_PAYL:
                    processor = invokeProcessor(msg -> {
                        return ReactiveStreams.fromPublisher(FlowAdapters.toPublisher(invoke(msg.getPayload())));
                    });
                    break;
                case PROCESSOR_PUBLISHER_BUILDER_MSG_2_MSG:
                    processor = invokeProcessor(this::invoke);
                    break;
                case PROCESSOR_PUBLISHER_BUILDER_PAYL_2_PAYL:
                    processor = invokeProcessor(msg -> invoke(msg.getPayload()));
                    break;
                case PROCESSOR_MSG_2_MSG:
                    processor = invokeProcessor(msg ->
                            ReactiveStreams.fromCompletionStageNullable(CompletableFuture.completedStage(invoke(msg))));
                    break;
                case PROCESSOR_PAYL_2_PAYL:
                    processor = invokeProcessor(msg -> ReactiveStreams.of((Object) invoke(msg.getPayload())));
                    break;
                case PROCESSOR_COMPL_STAGE_MSG_2_MSG:
                    processor = invokeProcessor(msg -> ReactiveStreams.fromCompletionStageNullable(invoke(msg)));
                    break;
                case PROCESSOR_COMPL_STAGE_PAYL_2_PAYL:
                    processor = invokeProcessor(msg -> ReactiveStreams.fromCompletionStage(invoke(msg.getPayload())));
                    break;
                default:
                    throw new MessagingDeploymentException("Invalid messaging method signature " + getMethod());
            }
        }
    }

    /**
     * Invoke processor method with ack/nack logic.
     *
     * @param publisherBuilder function for actual invoking processor method with
     *                         message parameter and publisher builder as a result.
     * @return processor created from invoked processor method
     */
    private Processor<Object, Object> invokeProcessor(Function<Message<?>, PublisherBuilder<Object>> publisherBuilder) {
        return ReactiveStreams.builder()
                .flatMap(in -> {
                    Message<?> inMsg = (Message<?>) in;
                    AckCtx ackCtx = AckCtx.create(this, inMsg);
                    try {
                        ackCtx.preAck();
                        return publisherBuilder.apply(inMsg)
                                .onError(ackCtx::postNack)
                                .onErrorResumeWith(this::resumeOnError)
                                .peek(out -> ackCtx.postAck())
                                .map(MessageUtils::wrap);
                    } catch (Throwable t) {
                        ackCtx.postNack(t);
                        return resumeOnError(t);
                    }
                })
                .buildRs();
    }

    private PublisherBuilder<Object> resumeOnError(Throwable t) {
        if (compatibilityMode.get()) {
            return ReactiveStreams.failed(t);
        }
        LOGGER.log(System.Logger.Level.ERROR, "Error intercepted in processor method "
                + this.getMethod().getDeclaringClass().getSimpleName() + "#" + this.getMethod().getName()
                + " incoming channel: " + this.getIncomingChannelName()
                + " outgoing channel: " + this.getOutgoingChannelName(), t);
        return ReactiveStreams.empty();
    }

    @SuppressWarnings("unchecked")
    <R> R invoke(Object incomingValue) {
        Method method = getMethod();
        try {
            return (R) method.invoke(getBeanInstance(), incomingValue);
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

    Processor<Object, Object> getProcessor() {
        return processor;
    }

    UniversalChannel getOutgoingChannel() {
        return outgoingChannel;
    }

    void setOutgoingChannel(UniversalChannel outgoingChannel) {
        this.outgoingChannel = outgoingChannel;
    }

    @Override
    public Publisher<?> getPublisher(String unused) {
        return getProcessor();
    }

    @Override
    public String getDescription() {
        return "processor " + getMethod().getName();
    }

    @Override
    public Subscriber<? super Object> getSubscriber(String unused) {
        return getProcessor();
    }
}
