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
import io.helidon.config.ConfigValue;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory;
import org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.reactivestreams.Subscriber;

import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.DeploymentException;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

/**
 * Subscriber with reference to {@link org.eclipse.microprofile.reactive.messaging.Incoming @Incoming}
 * /{@link org.eclipse.microprofile.reactive.messaging.Outgoing @Outgoing} annotated method
 */
public class IncomingChannelMethod extends AbstractChannelMethod {

    private static final Logger LOGGER = Logger.getLogger(IncomingChannelMethod.class.getName());

    private PublisherBuilder<? extends Message<?>> publisherBuilder;
    private Subscriber subscriber;

    public IncomingChannelMethod(AnnotatedMethod method, ChannelRouter router) {
        super(method.getAnnotation(Incoming.class).value(), null, method.getJavaMember(), router);
        resolveSignatureType();
    }

    void validate() {
        if (incomingChannelName == null || incomingChannelName.trim().isEmpty()) {
            throw new DeploymentException("Missing channel name in annotation @Incoming on method "
                    + method.toString());
        }
    }

    @Override
    public void init(BeanManager beanManager, Config config) {
        super.init(beanManager, config);
        if (type.isInvokeAtAssembly()) {
            // Incoming methods returning custom subscriber
            try {
                switch (type) {
                    case INCOMING_VOID_2_SUBSCRIBER:
                        subscriber = (Subscriber) method.invoke(beanInstance);
                        break;
                    case INCOMING_VOID_2_SUBSCRIBER_BUILDER:
                        subscriber = ((SubscriberBuilder) method.invoke(beanInstance)).build();
                        break;
                    default:
                        throw new DeploymentException("Unsupported method signature " + method);
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        } else {
            // Create brand new subscriber
            subscriber = new InternalSubscriber(method, beanInstance);
        }
    }

    @Override
    protected void connect() {
        Config channelConfig = config.get("mp.messaging.incoming").get(incomingChannelName);
        ConfigValue<String> connectorName = channelConfig.get("connector").asString();
        if (connectorName.isPresent()) {
            Config connectorConfig = config.get("mp.messaging.connector")
                    .get(connectorName.get());
            org.eclipse.microprofile.config.Config augmentedConfig =
                    AdHocConfigBuilder
                            .from(channelConfig)
                            //It seams useless but its required by the spec
                            .put(ConnectorFactory.CHANNEL_NAME_ATTRIBUTE, incomingChannelName)
                            .putAll(connectorConfig)
                            .build();
            publisherBuilder =
                    ((IncomingConnectorFactory) getBeanInstance(
                            getRouter().getIncomingConnectorFactory(connectorName.get()),
                            beanManager))
                            .getPublisherBuilder(augmentedConfig);

            //TODO: iterate over multiple publishers / does spec even support multiple publishers?
            publisherBuilder.buildRs().subscribe(this.subscriber);
            LOGGER.info(String.format("Connected channel %s to connector %s, method: %s", incomingChannelName, connectorName.get(), method.toString()));
            connected = true;
        }

    }

    public Subscriber getSubscriber() {
        return subscriber;
    }

    protected void resolveSignatureType() {
        Class<?> returnType = this.method.getReturnType();
        Class<?> parameterType = this.method.getParameterTypes()[0];
        if (Void.TYPE.equals(parameterType)) {
            if (Subscriber.class.equals(returnType)) {
                this.type = Type.INCOMING_VOID_2_SUBSCRIBER;
            } else if (SubscriberBuilder.class.equals(returnType)) {
                this.type = Type.INCOMING_VOID_2_SUBSCRIBER_BUILDER;
            }
        } else {
            if (CompletionStage.class.equals(returnType)) {
                this.type = Type.INCOMING_MSG_2_COMPLETION_STAGE;
            } else if (Void.TYPE.equals(returnType)) {
                this.type = Type.INCOMING_MSG_2_VOID;
            }
        }
    }

}
