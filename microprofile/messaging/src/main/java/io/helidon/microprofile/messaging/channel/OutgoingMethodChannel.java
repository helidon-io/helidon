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
import io.helidon.config.ConfigValue;
import io.helidon.microprofile.messaging.AdHocConfigBuilder;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory;
import org.eclipse.microprofile.reactive.messaging.spi.OutgoingConnectorFactory;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.reactivestreams.Publisher;

import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.DeploymentException;

import java.util.List;
import java.util.logging.Logger;

public class OutgoingMethodChannel extends AbstractChannel {

    private static final Logger LOGGER = Logger.getLogger(OutgoingMethodChannel.class.getName());

    private SubscriberBuilder<? extends Message<?>, Void> subscriberBuilder;

    public OutgoingMethodChannel(AnnotatedMethod method, ChannelRouter router) {
        super(null, method.getAnnotation(Outgoing.class).value(), method.getJavaMember(), router);
    }

    public void validate() {
        if (outgoingChannelName == null || outgoingChannelName.trim().isEmpty()) {
            throw new DeploymentException("Missing channel name in annotation @Outgoing, method: "
                    + method.toString());
        }
        if (method.getReturnType().equals(Void.TYPE)) {
            throw new DeploymentException("Method annotated as @Outgoing channel cannot have return type void, method: "
                    + method.toString());
        }
    }

    public void connect() {
        Config channelConfig = config.get("mp.messaging.outgoing").get(outgoingChannelName);
        ConfigValue<String> connectorName = channelConfig.get("connector").asString();
        if (connectorName.isPresent()) {
            //Connect to connector
            Config connectorConfig = config
                    .get("mp.messaging.connector")
                    .get(connectorName.get());
            org.eclipse.microprofile.config.Config augmentedConfig =
                    AdHocConfigBuilder
                            .from(channelConfig)
                            //It seams useless but its required by the spec
                            .put(ConnectorFactory.CHANNEL_NAME_ATTRIBUTE, outgoingChannelName)
                            .putAll(connectorConfig)
                            .build();
            subscriberBuilder = ((OutgoingConnectorFactory) getBeanInstance(getRouter()
                    .getOutgoingConnectorFactory(connectorName.get()), beanManager))
                    .getSubscriberBuilder(augmentedConfig);
            getPublisherBuilder()
                    .buildRs()
                    .subscribe(subscriberBuilder.build());
            connected = true;
        } else {
            // Connect to Incoming methods with publisher
            List<IncomingMethodChannel> incomingMethodChannels = getRouter().getIncomingSubscribers(getOutgoingChannelName());
            if (incomingMethodChannels != null) {
                for (IncomingMethodChannel s : getRouter().getIncomingSubscribers(getOutgoingChannelName())) {
                    System.out.println("Connecting " + this.getOutgoingChannelName() + " " + this.method.getName() + " to " + s.method.getName());

                    Publisher publisher = getPublisher();
                    if(s instanceof ProcessorMethodChannel){
                        // Processors managing subscribing
                        ((ProcessorMethodChannel)s).setPublisher(publisher);
                    }else{
                        // TODO: Move subscribing to Incoming methods to align with processors
                        publisher.subscribe(s.getSubscriber());
                    }
                    s.connected = true;
                    connected = true;
                }
            }

        }
    }
}
