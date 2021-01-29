/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.BeanManager;

import io.helidon.common.Errors;
import io.helidon.config.Config;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.reactivestreams.Publisher;


class OutgoingMethod extends AbstractMessagingMethod {

    private PublisherBuilder<?> publisher;

    OutgoingMethod(AnnotatedMethod<?> method, Errors.Collector errors) {
        super(method.getJavaMember(), errors);
        super.setOutgoingChannelName(method.getAnnotation(Outgoing.class).value());
    }

    @Override
    public void init(BeanManager beanManager, Config config) {
        super.init(beanManager, config);
        if (getType().isInvokeAtAssembly()) {
            switch (getType()) {
                case OUTGOING_PUBLISHER_MSG_2_VOID:
                    publisher = ReactiveStreams.fromPublisher(this.invoke())
                            .peek(o -> this.afterInvoke(null, o));
                    break;
                case OUTGOING_PUBLISHER_PAYL_2_VOID:
                    publisher = ReactiveStreams.fromPublisher(this.invoke())
                            .map(MessageUtils::wrap)
                            .peek(o -> this.afterInvoke(null, o));
                    break;
                case OUTGOING_PUBLISHER_BUILDER_MSG_2_VOID:
                    publisher = ((PublisherBuilder<?>) this.invoke())
                            .peek(o -> this.afterInvoke(null, o));
                    break;
                case OUTGOING_PUBLISHER_BUILDER_PAYL_2_VOID:
                    publisher = ((PublisherBuilder<?>) this.invoke())
                            .map(MessageUtils::wrap)
                            .peek(o -> this.afterInvoke(null, o));
                    break;
                default:
                    throw new UnsupportedOperationException(String
                            .format("Not implemented signature %s", getType()));
            }
        } else {
            // Invoke on each request publisher
            switch (getType()) {
                case OUTGOING_COMPLETION_STAGE_MSG_2_VOID:
                    publisher = ReactiveStreams
                            .generate(() -> (CompletionStage<?>) this.invoke())
                            .flatMapCompletionStage(Function.identity())
                            .peek(m -> this.afterInvoke(null, m));
                    break;
                case OUTGOING_COMPLETION_STAGE_PAYL_2_VOID:
                    publisher = ReactiveStreams
                            .generate(() -> (CompletionStage<?>) this.invoke())
                            .flatMapCompletionStage(Function.identity())
                            .map(MessageUtils::wrap)
                            .peek(m -> this.afterInvoke(null, m));
                    break;
                case OUTGOING_MSG_2_VOID:
                    publisher = ReactiveStreams
                            .generate(() -> (Message<?>) this.invoke())
                            .peek(m -> this.afterInvoke(null, m));
                    break;
                case OUTGOING_PAYL_2_VOID:
                    publisher = ReactiveStreams
                            .generate(this::invoke)
                            .map(MessageUtils::wrap)
                            .peek(m -> this.afterInvoke(null, m));
                    break;
                default:
                    throw new UnsupportedOperationException(String
                            .format("Not implemented signature %s", getType()));
            }

        }
    }

    @Override
    void validate() {
        super.validate();
        if (getOutgoingChannelName() == null || getOutgoingChannelName().trim().isEmpty()) {
            super.errors().fatal(String
                    .format("Missing channel name in annotation @Outgoing, method: %s", getMethod()));
        }
        if (getMethod().getReturnType().equals(Void.TYPE)) {
            super.errors().fatal(String.format("Method annotated as @Outgoing channel cannot have return type void, method: %s",
                    getMethod()));
        }
    }

    Publisher<?> getPublisher() {
        return publisher.buildRs();
    }

}
