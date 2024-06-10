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

import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.config.ConfigValue;

import jakarta.enterprise.inject.spi.DeploymentException;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

class UniversalChannel {

    private static final System.Logger LOGGER = System.getLogger(UniversalChannel.class.getName());

    private String name;
    private IncomingMember incomingMember;
    private OutgoingMember outgoingMember;
    private final Config config;
    private final ChannelRouter router;
    private UniversalChannel upstreamChannel;

    UniversalChannel(String name, ChannelRouter router) {
        this.router = router;
        this.config = router.getConfig();
        this.name = name;
    }

    void setIncoming(IncomingMethod incomingMethod) {
        if (incomingMember != null) {
            throw new DeploymentException(String.format("Multiple incoming of the channel %s, [%s, %s]",
                    name,
                    incomingMember.getDescription(),
                    incomingMethod.getDescription())
            );
        }
        this.name = incomingMethod.getIncomingChannelName();
        this.incomingMember = incomingMethod;
    }

    void setIncoming(ProcessorMethod processorMethod) {
        if (incomingMember != null) {
            throw new DeploymentException(String.format("Multiple incoming of the channel %s, [%s, %s]",
                    name,
                    incomingMember.getDescription(),
                    processorMethod.getDescription())
            );
        }
        this.name = processorMethod.getIncomingChannelName();
        processorMethod.setOutgoingChannel(this);
        this.incomingMember = processorMethod;
    }

    void setOutgoing(ProcessorMethod processorMethod) {
        if (outgoingMember != null) {
            throw new DeploymentException(String.format("Multiple outgoing of the channel %s, [%s, %s]",
                    name,
                    outgoingMember.getDescription(),
                    processorMethod.getDescription())
            );
        }
        this.name = processorMethod.getOutgoingChannelName();
        this.outgoingMember = processorMethod;
    }

    void setOutgoing(OutgoingMethod outgoingMethod) {
        if (outgoingMember != null) {
            throw new DeploymentException(String.format("Multiple outgoing of the channel %s, [%s, %s]",
                    name,
                    outgoingMember.getDescription(),
                    outgoingMethod.getDescription())
            );
        }
        this.name = outgoingMethod.getOutgoingChannelName();
        this.outgoingMember = outgoingMethod;
    }

    void setIncoming(IncomingPublisher injectedPublisher) {
        if (incomingMember != null) {
            throw new DeploymentException(String.format("Multiple incoming of the channel %s, [%s, %s]",
                    name,
                    incomingMember.getDescription(),
                    injectedPublisher.getDescription())
            );
        }
        this.name = injectedPublisher.getChannelName();
        this.incomingMember = injectedPublisher;
    }

    void setOutgoing(OutgoingEmitter emitter) {
        if (outgoingMember != null) {
            throw new DeploymentException(String.format("Multiple outgoing of the channel %s, [%s, %s]",
                    name,
                    outgoingMember.getDescription(),
                    emitter.getDescription())
            );
        }
        this.name = emitter.getChannelName();
        this.outgoingMember = emitter;
    }

    void connect() {
        router.getChannelProcessors().init();

        StringBuilder connectMessage = new StringBuilder("Connecting channel ")
                .append(name).append(" with outgoing method ");

        if (outgoingMember == null) {
            LOGGER.log(System.Logger.Level.ERROR, connectMessage.append("and no outgoing method found!").toString());
            throw ExceptionUtils.createNoOutgoingMethodForChannel(name);
        }

        Publisher<?> publisher = outgoingMember.getPublisher(name);
        connectMessage.append(outgoingMember.getDescription());

        if (outgoingMember instanceof ProcessorMethod) {
            upstreamChannel = ((ProcessorMethod) outgoingMember).getOutgoingChannel();
        }

        connectMessage.append(" and incoming method ");

        var optUpstreamChannel = Optional.ofNullable(this.upstreamChannel);

        if (incomingMember == null) {
            LOGGER.log(System.Logger.Level.ERROR, connectMessage.append("and no incoming method found!").toString());
            throw ExceptionUtils.createNoIncomingMethodForChannel(name);
        }

        Subscriber<? super Object> subscriber1 = incomingMember.getSubscriber(name);
        connectMessage.append(incomingMember.getDescription());
        channelProcessorsConnect(publisher, subscriber1);
        optUpstreamChannel.ifPresent(UniversalChannel::connect);
    }

    boolean isLastInChain() {
        return incomingMember == null || !(incomingMember instanceof ProcessorMethod);
    }

    void findConnectors() {
        //Looks suspicious but incoming connector configured for outgoing channel is ok
        ConfigValue<String> incomingConnectorName = config.get("mp.messaging.outgoing").get(name + ".connector").asString();
        ConfigValue<String> outgoingConnectorName = config.get("mp.messaging.incoming").get(name + ".connector").asString();
        if (incomingConnectorName.isPresent()) {
            if (incomingMember != null) {
                throw new DeploymentException(String.format("Multiple incoming of the channel %s, [%s, %s]",
                        name,
                        incomingMember.getDescription(),
                        "connector " + incomingConnectorName)
                );
            }
            incomingMember = router.getIncomingConnector(incomingConnectorName.get())
                    .orElseThrow(() -> ExceptionUtils.createNoConnectorFound(incomingConnectorName.get()));
        }
        if (outgoingConnectorName.isPresent()) {
            if (outgoingMember != null) {
                throw new DeploymentException(String.format("Multiple outgoing of the channel %s, [%s, %s]",
                        name,
                        outgoingMember.getDescription(),
                        "connector " + outgoingConnectorName)
                );
            }
            outgoingMember = router.getOutgoingConnector(outgoingConnectorName.get())
                    .orElseThrow(() -> ExceptionUtils.createNoConnectorFound(outgoingConnectorName.get()));
        }
    }

    @SuppressWarnings("unchecked")
    void channelProcessorsConnect(Publisher<?> publisher, Subscriber<?> subscriber) {
        router.getChannelProcessors()
                .connect(this.name, (Publisher<Message<?>>) publisher, (Subscriber<Message<?>>) subscriber);
    }

}
