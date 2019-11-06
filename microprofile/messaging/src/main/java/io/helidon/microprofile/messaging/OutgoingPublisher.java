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
import io.helidon.microprofile.config.MpConfig;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.messaging.spi.OutgoingConnectorFactory;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.DeploymentException;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

//TODO: remove publisher implementation, it doesnt make sense here(rename class too)
public class OutgoingPublisher extends AbstractConnectableChannelMethod implements Publisher<Message<?>> {

    private static final Logger LOGGER = Logger.getLogger(OutgoingPublisher.class.getName());

    private List<Subscriber<? super Message<?>>> subscriberList = new ArrayList<>();
    private SubscriberBuilder<? extends Message<?>, Void> subscriberBuilder;

    public OutgoingPublisher(AnnotatedMethod method, ChannelRouter router) {
        super(method.getAnnotation(Outgoing.class).value(), method.getJavaMember(), router);
    }

    void validate() {
        if (channelName == null || channelName.trim().isEmpty()) {
            throw new DeploymentException("Missing channel name in annotation @Outgoing, method: "
                    + method.toString());
        }
        if (method.getReturnType().equals(Void.TYPE)) {
            throw new DeploymentException("Method annotated as @Outgoing channel cannot have return type void, method: "
                    + method.toString());
        }
    }

    public void connect() {
        Config channelConfig = config.get("mp.messaging.outgoing").get(channelName);
        ConfigValue<String> connectorName = channelConfig.get("connector").asString();
        if (connectorName.isPresent()) {
            subscriberBuilder = ((OutgoingConnectorFactory) getBeanInstance(getRouter()
                    .getOutgoingConnectorFactory(connectorName.get()), beanManager))
                    .getSubscriberBuilder(MpConfig.builder().config(channelConfig).build());
            getPublisherBuilder().buildRs().subscribe(subscriberBuilder.build());
        } else {
            // Connect to Incoming methods
            List<IncomingSubscriber> incomingSubscribers = getRouter().getIncomingSubscribers(getChannelName());
            if (incomingSubscribers != null) {
                for (IncomingSubscriber s : getRouter().getIncomingSubscribers(getChannelName())) {
                    getPublisherBuilder()
                            .to(ReactiveStreams.fromSubscriber(s))
                            .run();
                }
            }

        }
    }

    private PublisherBuilder getPublisherBuilder() {
        try {
            Object returnInstance = method.invoke(beanInstance);
            if (returnInstance instanceof Publisher) {
                // Called once at assembly time.
                return ReactiveStreams.fromPublisher((Publisher) returnInstance);
            } else if (returnInstance instanceof PublisherBuilder) {
                // Called once at assembly time.
                return (PublisherBuilder) returnInstance;
            } else if (returnInstance instanceof Message) {
                //TODO: Supported method signatures in the spec - Message !!!
                // Called for each request made by the subscriber
                throw new UnsupportedOperationException("Not implemented yet!!");
            } else {
                //TODO: Supported method signatures in the spec - Any type
                // Called for each request made by the subscriber
                throw new UnsupportedOperationException("Not implemented yet!!");
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void subscribe(Subscriber<? super Message<?>> subscriber) {
        //TODO: Remove whole publisher signature from this class
        throw new UnsupportedOperationException();
    }
}
