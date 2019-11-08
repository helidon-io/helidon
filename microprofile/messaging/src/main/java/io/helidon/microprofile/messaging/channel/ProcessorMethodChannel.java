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
import io.helidon.microprofile.messaging.reactive.OutgoingConnectorProcessor;
import io.helidon.microprofile.messaging.reactive.InternalProcessor;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory;
import org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory;
import org.eclipse.microprofile.reactive.messaging.spi.OutgoingConnectorFactory;
import org.eclipse.microprofile.reactive.streams.operators.ProcessorBuilder;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.DeploymentException;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

public class ProcessorMethodChannel extends IncomingMethodChannel {

    private static final Logger LOGGER = Logger.getLogger(ProcessorMethodChannel.class.getName());

    private SubscriberBuilder<? extends Message<?>, Void> subscriberBuilder;
    private Processor<Object, Object> processor;
    private Publisher<Object> publisher;

    public ProcessorMethodChannel(AnnotatedMethod method, ChannelRouter router) {
        super(method, router);
        super.outgoingChannelName =
                method.getAnnotation(Outgoing.class).value();
        resolveSignatureType();
    }

    @Override
    public void init(BeanManager beanManager, Config config) {
        super.init(beanManager, config);
        if (type.isInvokeAtAssembly()) {
            // TODO: Incoming methods returning custom processor
            throw new UnsupportedOperationException("Not implemented yet!");
        } else {
            // Create brand new subscriber
            processor = new InternalProcessor(this);
        }
    }

    @Override
    public void validate() {
        super.validate();
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

    @Override
    protected void connect() {
        //TODO: Extract connectors to UpstreamConnectorChannel and DownstreamConnectorChannel also rename channelMethods to channels only
        //Connect to upstream incoming connector if any
        Config incomingChannelConfig = config.get("mp.messaging.incoming").get(incomingChannelName);
        ConfigValue<String> connectorName = incomingChannelConfig.get("connector").asString();
        if (connectorName.isPresent()) {
            Config connectorConfig = config.get("mp.messaging.connector")
                    .get(connectorName.get());
            org.eclipse.microprofile.config.Config augmentedConfig =
                    AdHocConfigBuilder
                            .from(incomingChannelConfig)
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
            publisher = (Publisher) publisherBuilder.buildRs();
            LOGGER.info(String.format("Connected channel %s to connector %s, method: %s", incomingChannelName, connectorName.get(), method.toString()));
            connected = true;
        }

        //Connect to downstream outgoing connector if any
        Config outgoingChannelConfig = config.get("mp.messaging.outgoing").get(outgoingChannelName);
        ConfigValue<String> outgoingConnectorName = outgoingChannelConfig.get("connector").asString();
        if (outgoingConnectorName.isPresent()) {
            //Connect to connector
            Config connectorConfig = config
                    .get("mp.messaging.connector")
                    .get(outgoingConnectorName.get());
            org.eclipse.microprofile.config.Config augmentedConfig =
                    AdHocConfigBuilder
                            .from(outgoingChannelConfig)
                            //It seams useless but its required by the spec
                            .put(ConnectorFactory.CHANNEL_NAME_ATTRIBUTE, outgoingChannelName)
                            .putAll(connectorConfig)
                            .build();
            subscriberBuilder = ((OutgoingConnectorFactory) getBeanInstance(getRouter()
                    .getOutgoingConnectorFactory(outgoingConnectorName.get()), beanManager))
                    .getSubscriberBuilder(augmentedConfig);
            processor = new OutgoingConnectorProcessor(this);
            processor.subscribe((Subscriber) subscriberBuilder.build());
            connected = true;
        } else {
            // Connect to downstream Incoming methods with publisher
            List<IncomingMethodChannel> incomingMethodChannels = getRouter().getIncomingSubscribers(getOutgoingChannelName());
            if (incomingMethodChannels != null) {
                for (IncomingMethodChannel s : getRouter().getIncomingSubscribers(getOutgoingChannelName())) {
                    System.out.println("Connecting " + this.getOutgoingChannelName() + " " + this.method.getName() + " to " + s.method.getName());
                    connected = true;
                    s.connected = true;
                    processor.subscribe(s.getSubscriber());
                }
            }
        }
        //Publisher is populated by upstream outgoing(OutgoingChannelMethod) or connector
        publisher.subscribe(processor);
    }

    public void setPublisher(Publisher<Object> publisher) {
        this.publisher = publisher;
    }

    protected void resolveSignatureType() {
        Class<?> returnType = this.method.getReturnType();
        Class<?> parameterType = this.method.getParameterTypes()[0];
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
