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
import io.helidon.microprofile.messaging.connector.IncomingConnector;
import io.helidon.microprofile.messaging.connector.OutgoingConnector;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import javax.enterprise.inject.spi.DeploymentException;

import java.util.Optional;

public class UniversalChannel {
    private String name;
    private IncomingConnector incomingConnector;
    private ProcessorMethod incomingProcessorMethod;
    private IncomingMethod incomingMethod;
    private OutgoingMethod outgoingMethod;
    private OutgoingConnector outgoingConnector;
    private ProcessorMethod outgoingProcessorMethod;
    private Publisher publisher;
    private Subscriber subscriber;
    private Config config;
    private ChannelRouter router;
    private Optional<UniversalChannel> upstreamChannel = Optional.empty();

    public UniversalChannel(ChannelRouter router) {
        this.router = router;
        this.config = router.getConfig();
    }

    public void setIncoming(IncomingMethod incomingMethod) {
        this.name = incomingMethod.getIncomingChannelName();
        this.incomingMethod = incomingMethod;
    }

    public void setIncoming(ProcessorMethod processorMethod) {
        this.name = processorMethod.getIncomingChannelName();
        this.incomingProcessorMethod = processorMethod;
        this.incomingProcessorMethod.setOutgoingChannel(this);
    }

    public void setOutgoing(ProcessorMethod processorMethod) {
        this.name = processorMethod.getOutgoingChannelName();
        this.outgoingProcessorMethod = processorMethod;
        this.outgoingProcessorMethod.setIncomingChannel(this);
    }

    public void setOutgoing(OutgoingMethod outgoingMethod) {
        this.name = outgoingMethod.getOutgoingChannelName();
        this.outgoingMethod = outgoingMethod;
    }

    public String getName() {
        return name;
    }

    public void connect() {
        if (outgoingMethod != null) {
            publisher = outgoingMethod.getPublisher();
            System.out.print(outgoingMethod.method.getName() + " >> ");
        } else if (outgoingProcessorMethod != null) {
            publisher = outgoingProcessorMethod.getProcessor();
            upstreamChannel = Optional.of(outgoingProcessorMethod.getOutgoingChannel());
            System.out.print(outgoingProcessorMethod.method.getName() + " >> ");
        } else if(outgoingConnector != null){
            publisher = outgoingConnector.getPublisher(name);
            System.out.print(outgoingConnector.getConnectorName() + " >> ");
        } else {
            throw new DeploymentException("No outgoing channel " + name + " found!");
        }

        if (incomingMethod != null) {
            subscriber = incomingMethod.getSubscriber();
            System.out.println(name + " >> " + incomingMethod.method.getName());
            publisher.subscribe(subscriber);
            //Continue connecting processor chain
            upstreamChannel.ifPresent(UniversalChannel::connect);
        } else if (incomingProcessorMethod != null) {
            subscriber = incomingProcessorMethod.getProcessor();
            System.out.println(name + " >> " + incomingProcessorMethod.method.getName());
            publisher.subscribe(subscriber);
            //Continue connecting processor chain
            upstreamChannel.ifPresent(UniversalChannel::connect);
        } else if (incomingConnector != null) {
            Subscriber subscriber = incomingConnector.getSubscriber(name);
            System.out.println(name + " >> " + incomingConnector.getConnectorName());
            publisher.subscribe(subscriber);
            //Continue connecting processor chain
            upstreamChannel.ifPresent(UniversalChannel::connect);
        } else {
            throw new DeploymentException("No incoming channel " + name + " found!");
        }
    }

    public boolean isLastInChain() {
        return incomingProcessorMethod == null;
    }

    public void findConnectors() {
        ConfigValue<String> incomingConnectorName = config.get("mp.messaging.outgoing").get(name).get("connector").asString();
        ConfigValue<String> outgoingConnectorName = config.get("mp.messaging.incoming").get(name).get("connector").asString();
        if (incomingConnectorName.isPresent()) {
            incomingConnector = router.getIncomingConnector(incomingConnectorName.get())
                    .orElseThrow(() -> new DeploymentException("No connector " + incomingConnectorName.get() + " found!"));
        }
        if (outgoingConnectorName.isPresent()) {
            outgoingConnector = router.getOutgoingConnector(outgoingConnectorName.get())
                    .orElseThrow(() -> new DeploymentException("No connector " + outgoingConnectorName.get() + " found!"));
        }
    }

}
