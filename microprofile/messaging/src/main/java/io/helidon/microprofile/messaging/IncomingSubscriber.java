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
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory;
import org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.DeploymentException;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Subscriber with reference to {@link org.eclipse.microprofile.reactive.messaging.Incoming @Incoming}
 * /{@link org.eclipse.microprofile.reactive.messaging.Outgoing @Outgoing} annotated method
 */
public class IncomingSubscriber extends AbstractConnectableChannelMethod implements Subscriber<Object> {

    private static final Logger LOGGER = Logger.getLogger(IncomingSubscriber.class.getName());

    private PublisherBuilder<? extends Message<?>> publisherBuilder;
    private Subscription subscription;
    private Long chunkSize = 5L;
    private Long chunkPosition = 0L;

    public IncomingSubscriber(AnnotatedMethod method, ChannelRouter router) {
        super(method.getAnnotation(Incoming.class).value(), method.getJavaMember(), router);
    }

    void validate() {
        if (channelName == null || channelName.trim().isEmpty()) {
            throw new DeploymentException("Missing channel name in annotation @Incoming on method "
                    + method.toString());
        }
    }

    @Override
    protected void connect() {
        Config channelConfig = config.get("mp.messaging.incoming").get(channelName);
        ConfigValue<String> connectorName = channelConfig.get("connector").asString();
        if (connectorName.isPresent()) {
            Config connectorConfig = config.get("mp.messaging.connector")
                    .get(connectorName.get());
            org.eclipse.microprofile.config.Config augmentedConfig =
                    AdHocConfigBuilder
                            .from(channelConfig)
                            //It seams useless but its required by the spec
                            .put(ConnectorFactory.CHANNEL_NAME_ATTRIBUTE, channelName)
                            .putAll(connectorConfig)
                            .build();
            publisherBuilder =
                    ((IncomingConnectorFactory) getBeanInstance(
                            getRouter().getIncomingConnectorFactory(connectorName.get()),
                            beanManager))
                            .getPublisherBuilder(augmentedConfig);

            //TODO: iterate over multiple publishers / does spec even support multiple publishers?
            publisherBuilder.buildRs().subscribe(this);
            LOGGER.info(String.format("Connected channel %s to connector %s, method: %s", channelName, connectorName.get(), method.toString()));
        }
    }


    public PublisherBuilder<? extends Message<?>> getPublisherBuilder() {
        return publisherBuilder;
    }

    private void incrementAndCheckChunkPosition() {
        chunkPosition++;
        if (chunkPosition >= chunkSize) {
            chunkPosition = 0L;
            subscription.request(chunkSize);
        }
    }

    @Override
    public void onNext(Object message) {
        try {
            final Object paramValue;
            Class<?> paramType = this.method.getParameterTypes()[0];

            if (paramType != Message.class && !(message instanceof Message)) {
                paramValue = paramType.cast(message);

            } else if (paramType == Message.class && message instanceof Message) {
                paramValue = paramType.cast(message);

            } else if (paramType != Message.class && message instanceof Message) {
                paramValue = paramType.cast(((Message) message).getPayload());

            } else if (paramType == Message.class && !(message instanceof Message)) {
                paramValue = paramType.cast(Message.of(message));

            } else {
                paramValue = message;
            }

            Context parentContext = Context.create();
            Context context = Context
                    .builder()
                    .parent(parentContext)
                    .id(parentContext.id() + ":message-" + UUID.randomUUID().toString())
                    .build();
            Contexts.runInContext(context, () -> this.method.invoke(this.beanInstance, paramValue));
            incrementAndCheckChunkPosition();
        } catch (Exception e) {
            //Notify publisher to stop sending
            subscription.cancel();
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onError(Throwable t) {
        throw new RuntimeException(t);
    }

    @Override
    public void onSubscribe(Subscription s) {
        subscription = s;
        //First chunk request
        subscription.request(chunkSize);
    }

    @Override
    public void onComplete() {
    }

}
