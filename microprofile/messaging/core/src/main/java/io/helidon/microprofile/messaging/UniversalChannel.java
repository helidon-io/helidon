/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.config.ConfigValue;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

class UniversalChannel {

    private static final Logger LOGGER = Logger.getLogger(UniversalChannel.class.getName());

    private String name;
    private IncomingConnector incomingConnector;
    private ProcessorMethod incomingProcessorMethod;
    private IncomingMethod incomingMethod;
    private OutgoingMethod outgoingMethod;
    private OutgoingConnector outgoingConnector;
    private ProcessorMethod outgoingProcessorMethod;
    private final Config config;
    private final ChannelRouter router;
    private UniversalChannel upstreamChannel;
    private final AtomicBoolean live = new AtomicBoolean(true);
    private final AtomicBoolean ready = new AtomicBoolean(false);

    UniversalChannel(ChannelRouter router) {
        this.router = router;
        this.config = router.getConfig();
    }

    void setIncoming(IncomingMethod incomingMethod) {
        this.name = incomingMethod.getIncomingChannelName();
        this.incomingMethod = incomingMethod;
    }

    void setIncoming(ProcessorMethod processorMethod) {
        this.name = processorMethod.getIncomingChannelName();
        this.incomingProcessorMethod = processorMethod;
        this.incomingProcessorMethod.setOutgoingChannel(this);
    }

    void setOutgoing(ProcessorMethod processorMethod) {
        this.name = processorMethod.getOutgoingChannelName();
        this.outgoingProcessorMethod = processorMethod;
    }

    void setOutgoing(OutgoingMethod outgoingMethod) {
        this.name = outgoingMethod.getOutgoingChannelName();
        this.outgoingMethod = outgoingMethod;
    }

    AtomicBoolean isLive() {
        return live;
    }

    AtomicBoolean isReady() {
        return ready;
    }

    void connect() {
        StringBuilder connectMessage = new StringBuilder("Connecting channel ")
                .append(name).append(" with outgoing method ");

        Publisher<?> publisher;
        if (outgoingMethod != null) {
            publisher = outgoingMethod.getPublisher();
            connectMessage.append(outgoingMethod.getMethod().getName());

        } else if (outgoingProcessorMethod != null) {
            publisher = outgoingProcessorMethod.getProcessor();
            upstreamChannel = outgoingProcessorMethod.getOutgoingChannel();
            connectMessage.append(outgoingProcessorMethod.getMethod().getName());

        } else if (outgoingConnector != null) {
            publisher = outgoingConnector.getPublisher(name);
            connectMessage.append(outgoingConnector.getConnectorName());
        } else {
            LOGGER.severe(connectMessage.append("and no outgoing method found!").toString());
            throw ExceptionUtils.createNoOutgoingMethodForChannel(name);
        }

        connectMessage.append(" and incoming method ");

        var optUpstreamChannel = Optional.ofNullable(this.upstreamChannel);

        Subscriber<? super Object> subscriber1;
        if (incomingMethod != null) {
            subscriber1 = incomingMethod.getSubscriber();
            connectMessage.append(incomingMethod.getMethod().getName());
            ChannelHealthProbe.connect(publisher, subscriber1, live, ready);
            //Continue connecting processor chain
            optUpstreamChannel.ifPresent(UniversalChannel::connect);

        } else if (incomingProcessorMethod != null) {
            subscriber1 = incomingProcessorMethod.getProcessor();
            connectMessage.append(incomingProcessorMethod.getMethod().getName());
            ChannelHealthProbe.connect(publisher, subscriber1, live, ready);
            //Continue connecting processor chain
            optUpstreamChannel.ifPresent(UniversalChannel::connect);

        } else if (incomingConnector != null) {
            subscriber1 = incomingConnector.getSubscriber(name);
            connectMessage.append(incomingConnector.getConnectorName());
            ChannelHealthProbe.connect(publisher, subscriber1, live, ready);
            //Continue connecting processor chain
            optUpstreamChannel.ifPresent(UniversalChannel::connect);

        } else {
            LOGGER.severe(connectMessage.append("and no incoming method found!").toString());
            throw ExceptionUtils.createNoIncomingMethodForChannel(name);
        }
    }

    boolean isLastInChain() {
        return incomingProcessorMethod == null;
    }

    void findConnectors() {
        //Looks suspicious but incoming connector configured for outgoing channel is ok
        ConfigValue<String> incomingConnectorName = config.get("mp.messaging.outgoing").get(name).get("connector").asString();
        ConfigValue<String> outgoingConnectorName = config.get("mp.messaging.incoming").get(name).get("connector").asString();
        if (incomingConnectorName.isPresent()) {
            incomingConnector = router.getIncomingConnector(incomingConnectorName.get())
                    .orElseThrow(() -> ExceptionUtils.createNoConnectorFound(incomingConnectorName.get()));
        }
        if (outgoingConnectorName.isPresent()) {
            outgoingConnector = router.getOutgoingConnector(outgoingConnectorName.get())
                    .orElseThrow(() -> ExceptionUtils.createNoConnectorFound(outgoingConnectorName.get()));
        }
    }

}
