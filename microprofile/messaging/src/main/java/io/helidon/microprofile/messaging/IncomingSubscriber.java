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

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.config.Config;
import io.helidon.config.ConfigValue;
import io.helidon.microprofile.config.MpConfig;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import javax.enterprise.inject.spi.AnnotatedMethod;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Subscriber with reference to {@link org.eclipse.microprofile.reactive.messaging.Incoming @Incoming}
 * /{@link org.eclipse.microprofile.reactive.messaging.Outgoing @Outgoing} annotated method
 */
public class IncomingSubscriber extends AbstractConnectableChannelMethod implements Subscriber<Message<?>> {

    private static final Logger LOGGER = Logger.getLogger(IncomingSubscriber.class.getName());

    private PublisherBuilder<? extends Message<?>> publisherBuilder;

    public IncomingSubscriber(AnnotatedMethod method, ChannelRouter router) {
        super(method.getAnnotation(Incoming.class).value(), method.getJavaMember(), router);
    }

    @Override
    protected void connect() {
        Config channelConfig = config.get("mp.messaging.incoming").get(channelName);
        ConfigValue<String> connectorName = channelConfig.get("connector").asString();
        if (connectorName.isPresent()) {
            publisherBuilder = ((IncomingConnectorFactory) getBeanInstance(getRouter()
                    .getIncomingConnectorFactory(connectorName.get()), beanManager))
                    .getPublisherBuilder(MpConfig.builder().config(channelConfig).build());

            //TODO: iterate over multiple publishers
            publisherBuilder.buildRs().subscribe(this);
            LOGGER.info(String.format("Connected channel %s to connector %s, method: %s", channelName, connectorName.get(), method.toString()));
        }
    }


    public PublisherBuilder<? extends Message<?>> getPublisherBuilder() {
        return publisherBuilder;
    }

    @Override
    public void onNext(Message<?> message) {
        try {
            final Object paramValue;
            Class<?> paramType = this.method.getParameterTypes()[0];
            if (paramType != Message.class) {
                paramValue = paramType.cast(message.getPayload());
            } else {
                paramValue = message;
            }

            Context parentContext = Context.create();
            Context context = Context
                    .builder()
                    .parent(parentContext)
                    .id(parentContext.id() + ":message-" + UUID.randomUUID().toString())
                    .build();
            Contexts.runInContext(context, () -> method.invoke(beanInstance, paramValue));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onError(Throwable t) {
        //TODO: Error propagation
    }

    @Override
    public void onSubscribe(Subscription s) {
        System.out.println(s);
    }

    @Override
    public void onComplete() {
    }

}
