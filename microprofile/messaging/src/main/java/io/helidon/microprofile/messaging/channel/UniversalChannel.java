/*
 * Copyright (c)  2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Optional;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.config.ConfigValue;
import io.helidon.microprofile.messaging.NoConnectorFoundException;
import io.helidon.microprofile.messaging.NotConnectableChannelException;
import io.helidon.microprofile.messaging.connector.IncomingConnector;
import io.helidon.microprofile.messaging.connector.OutgoingConnector;

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
    private Publisher publisher;
    private Config config;
    private ChannelRouter router;
    private Optional<UniversalChannel> upstreamChannel = Optional.empty();

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

    @SuppressWarnings("unchecked")
    void connect() {
        StringBuilder connectMessage = new StringBuilder("Connecting channel ")
                .append(name).append(" with outgoing method ");

        if (outgoingMethod != null) {
            publisher = outgoingMethod.getPublisher();
            connectMessage.append(outgoingMethod.getMethod().getName());

        } else if (outgoingProcessorMethod != null) {
            publisher = outgoingProcessorMethod.getProcessor();
            upstreamChannel = Optional.of(outgoingProcessorMethod.getOutgoingChannel());
            connectMessage.append(outgoingProcessorMethod.getMethod().getName());

        } else if (outgoingConnector != null) {
            publisher = outgoingConnector.getPublisher(name);
            connectMessage.append(outgoingConnector.getConnectorName());
        } else {
            LOGGER.severe(connectMessage.append("and no outgoing method found!").toString());
            throw new NotConnectableChannelException(name, NotConnectableChannelException.Type.OUTGOING);
        }

        connectMessage.append(" and incoming method ");

        Subscriber subscriber1;
        if (incomingMethod != null) {
            subscriber1 = incomingMethod.getSubscriber();
            connectMessage.append(incomingMethod.getMethod().getName());
            publisher.subscribe(subscriber1);
            //Continue connecting processor chain
            upstreamChannel.ifPresent(UniversalChannel::connect);

        } else if (incomingProcessorMethod != null) {
            subscriber1 = incomingProcessorMethod.getProcessor();
            connectMessage.append(incomingProcessorMethod.getMethod().getName());
            publisher.subscribe(subscriber1);
            //Continue connecting processor chain
            upstreamChannel.ifPresent(UniversalChannel::connect);

        } else if (incomingConnector != null) {
            subscriber1 = incomingConnector.getSubscriber(name);
            connectMessage.append(incomingConnector.getConnectorName());
            publisher.subscribe(subscriber1);
            //Continue connecting processor chain
            upstreamChannel.ifPresent(UniversalChannel::connect);

        } else {
            LOGGER.severe(connectMessage.append("and no incoming method found!").toString());
            throw new NotConnectableChannelException(name, NotConnectableChannelException.Type.INCOMING);
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
                    .orElseThrow(() -> new NoConnectorFoundException(incomingConnectorName.get()));
        }
        if (outgoingConnectorName.isPresent()) {
            outgoingConnector = router.getOutgoingConnector(outgoingConnectorName.get())
                    .orElseThrow(() -> new NoConnectorFoundException(outgoingConnectorName.get()));
        }
    }

}
