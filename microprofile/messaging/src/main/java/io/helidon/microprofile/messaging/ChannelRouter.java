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
import io.helidon.microprofile.config.MpConfig;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory;
import org.eclipse.microprofile.reactive.messaging.spi.OutgoingConnectorFactory;

import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.DeploymentException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChannelRouter {
    private List<AbstractChannelMethod> connectableBeanMethods = new ArrayList<>();
    private Map<String, List<IncomingChannelMethod>> incomingSubscriberMap = new HashMap<>();
    private Map<String, List<AbstractChannelMethod>> outgoingSubscriberMap = new HashMap<>();

    private Map<String, Bean<?>> incomingConnectorFactoryMap = new HashMap<>();
    private Map<String, Bean<?>> outgoingConnectorFactoryMap = new HashMap<>();

    public void registerBeanReference(Bean<?> bean) {
        connectableBeanMethods.stream()
                .filter(m -> m.getDeclaringType() == bean.getBeanClass())
                .forEach(m -> m.setDeclaringBean(bean));
    }

    public void connect(BeanManager beanManager) {
        Config config = ((MpConfig) ConfigProvider.getConfig()).helidonConfig();
        //Needs to be initialized before connecting,
        // fast publishers would call onNext before all bean references are resolved
        connectableBeanMethods.forEach(m -> m.init(beanManager, config));
        connectableBeanMethods.stream().filter(OutgoingChannelMethod.class::isInstance).forEach(AbstractChannelMethod::connect);
        connectableBeanMethods.stream().filter(IncomingChannelMethod.class::isInstance).forEach(AbstractChannelMethod::connect);
        connectableBeanMethods.stream().filter(m -> !m.connected).forEach(m -> {
            throw new DeploymentException("Channel " + m.incomingChannelName + "/" + m.outgoingChannelName
                    + " has no candidate to connect to method: " + m.method);
        });
//        connectableBeanMethods.stream().filter(ProcessorChannelMethod.class::isInstance).forEach(AbstractChannelMethod::connect);
    }

    void addIncomingMethod(AnnotatedMethod m) {
        IncomingChannelMethod incomingChannelMethod = new IncomingChannelMethod(m, this);
        incomingChannelMethod.validate();
        String channelName = incomingChannelMethod.getIncomingChannelName();
        getIncomingSubscribers(channelName).add(incomingChannelMethod);
        connectableBeanMethods.add(incomingChannelMethod);
    }

    void addOutgoingMethod(AnnotatedMethod m) {
        OutgoingChannelMethod outgoingChannelMethod = new OutgoingChannelMethod(m, this);
        outgoingChannelMethod.validate();
        String channelName = outgoingChannelMethod.getOutgoingChannelName();
        getOutgoingSubscribers(channelName).add(outgoingChannelMethod);
        connectableBeanMethods.add(outgoingChannelMethod);
    }

    void addProcessorMethod(AnnotatedMethod m) {
        ProcessorChannelMethod channelMethod = new ProcessorChannelMethod(m, this);
        channelMethod.validate();
        getIncomingSubscribers(channelMethod.getIncomingChannelName()).add(channelMethod);
        getOutgoingSubscribers(channelMethod.getOutgoingChannelName()).add(channelMethod);
        connectableBeanMethods.add(channelMethod);
    }

    public void addMethod(AnnotatedMethod<?> m) {
        if (m.isAnnotationPresent(Incoming.class) && m.isAnnotationPresent(Outgoing.class)) {
            this.addProcessorMethod(m);
        } else if (m.isAnnotationPresent(Incoming.class)) {
            this.addIncomingMethod(m);
        } else if (m.isAnnotationPresent(Outgoing.class)) {
            this.addOutgoingMethod(m);
        }
    }

    void addConnectorFactory(Bean<?> bean) {
        Class<?> beanType = bean.getBeanClass();
        Connector annotation = beanType.getAnnotation(Connector.class);
        if (IncomingConnectorFactory.class.isAssignableFrom(beanType) && null != annotation) {
            incomingConnectorFactoryMap.put(annotation.value(), bean);
        }
        if (OutgoingConnectorFactory.class.isAssignableFrom(beanType) && null != annotation) {
            outgoingConnectorFactoryMap.put(annotation.value(), bean);
        }
    }

    public List<IncomingChannelMethod> getIncomingSubscribers(String channelName) {
        return getOrCreateList(channelName, incomingSubscriberMap);
    }

    public List<AbstractChannelMethod> getOutgoingSubscribers(String channelName) {
        return getOrCreateList(channelName, outgoingSubscriberMap);
    }

    private static <T> List<T> getOrCreateList(String key, Map<String, List<T>> map) {
        List<T> list = map.getOrDefault(key, new ArrayList<>());
        if (list.isEmpty()) {
            map.put(key, list);
        }
        return list;
    }

    public Bean<?> getIncomingConnectorFactory(String connectorName) {
        return incomingConnectorFactoryMap.get(connectorName);
    }

    public Bean<?> getOutgoingConnectorFactory(String connectorName) {
        return outgoingConnectorFactoryMap.get(connectorName);
    }
}
