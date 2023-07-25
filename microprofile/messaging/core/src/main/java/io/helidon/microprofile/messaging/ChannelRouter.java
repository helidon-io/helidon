/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import io.helidon.common.Errors;
import io.helidon.config.Config;

import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.inject.spi.DeploymentException;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory;
import org.eclipse.microprofile.reactive.messaging.spi.OutgoingConnectorFactory;

/**
 * Orchestrator for all found channels, methods and connectors.
 */
class ChannelRouter {
    private static final Logger LOGGER = Logger.getLogger(ChannelRouter.class.getName());

    private final Errors.Collector errors = Errors.collector();
    private final Config config = (Config) ConfigProvider.getConfig();

    private final List<AbstractMessagingMethod> connectableBeanMethods = new ArrayList<>();

    private final Map<String, UniversalChannel> channelMap = new HashMap<>();
    private final Map<String, IncomingConnector> incomingConnectorMap = new HashMap<>();
    private final Map<String, OutgoingConnector> outgoingConnectorMap = new HashMap<>();

    private final List<Bean<?>> incomingConnectorFactoryList = new ArrayList<>();
    private final List<Bean<?>> outgoingConnectorFactoryList = new ArrayList<>();
    private final List<Bean<?>> channelProcessorBeans = new ArrayList<>();
    private final ChannelProcessors channelProcessors = new ChannelProcessors();
    private BeanManager beanManager;

    /**
     * Register bean reference with at least one annotated messaging method.
     *
     * @param bean {@link jakarta.enterprise.inject.spi.Bean} with messaging methods reference
     * @see org.eclipse.microprofile.reactive.messaging.Incoming
     * @see org.eclipse.microprofile.reactive.messaging.Outgoing
     */
    void registerBeanReference(Bean<?> bean) {
        connectableBeanMethods.stream()
                .filter(m -> m.getDeclaringType() == bean.getBeanClass())
                .forEach(m -> m.setDeclaringBean(bean));
    }

    /**
     * Connect all discovered channel graphs.
     *
     * @param beanManager {@link jakarta.enterprise.inject.spi.BeanManager} for looking-up bean instances of discovered methods
     */
    void connect(BeanManager beanManager) {
        this.beanManager = beanManager;
        //Needs to be initialized before connecting,
        // fast publishers would call onNext before all bean references are resolved
        incomingConnectorFactoryList.forEach(this::addOutgoingConnector);
        outgoingConnectorFactoryList.forEach(this::addIncomingConnector);
        channelProcessorBeans.forEach(this::addChannelProcessor);
        connectableBeanMethods.forEach(m -> m.init(beanManager, config));

        channelMap.values().forEach(UniversalChannel::findConnectors);
        channelMap.values().stream().filter(UniversalChannel::isLastInChain).forEach(UniversalChannel::connect);
    }

    /**
     * Register messaging method.
     *
     * @param m {@link jakarta.enterprise.inject.spi.AnnotatedMethod}
     *          with {@link org.eclipse.microprofile.reactive.messaging.Incoming @Incoming}
     *          or {@link org.eclipse.microprofile.reactive.messaging.Outgoing @Outgoing} annotation
     */
    void registerMethod(AnnotatedMethod<?> m) {
        if (m.isAnnotationPresent(Incoming.class) && m.isAnnotationPresent(Outgoing.class)) {
            this.addProcessorMethod(m);
        } else if (m.isAnnotationPresent(Incoming.class)) {
            this.addIncomingMethod(m);
        } else if (m.isAnnotationPresent(Outgoing.class)) {
            this.addOutgoingMethod(m);
        }
    }

    void registerEmitter(OutgoingEmitter emitter) {
        this.getOrCreateChannel(emitter.getChannelName()).setOutgoing(emitter);
    }

    void registerPublisher(IncomingPublisher injectedPublisher) {
        this.getOrCreateChannel(injectedPublisher.getChannelName()).setIncoming(injectedPublisher);
    }

    /**
     * Register connector bean, can be recognized as a bean implementing
     * {@link org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory}
     * or {@link org.eclipse.microprofile.reactive.messaging.spi.OutgoingConnectorFactory}
     * or both with annotation {@link org.eclipse.microprofile.reactive.messaging.spi.Connector}.
     *
     * @param bean connector bean
     */
    void registerConnectorFactory(Bean<?> bean) {
        Class<?> beanType = bean.getBeanClass();
        Connector annotation = beanType.getAnnotation(Connector.class);
        if (IncomingConnectorFactory.class.isAssignableFrom(beanType) && null != annotation) {
            incomingConnectorFactoryList.add(bean);
        }
        if (OutgoingConnectorFactory.class.isAssignableFrom(beanType) && null != annotation) {
            outgoingConnectorFactoryList.add(bean);
        }
    }

    Config getConfig() {
        return config;
    }

    Optional<IncomingConnector> getIncomingConnector(String connectorName) {
        return Optional.ofNullable(incomingConnectorMap.get(connectorName));
    }

    Optional<OutgoingConnector> getOutgoingConnector(String connectorName) {
        return Optional.ofNullable(outgoingConnectorMap.get(connectorName));
    }

    void validate(){
        boolean hasFatal = errors.hasFatal();
        Errors errorMessages = errors.collect();
        if (hasFatal) {
            throw new DefinitionException(errorMessages.toString());
        } else {
            errorMessages.log(LOGGER);
        }
        getConfig().get("mp.messaging.outgoing")
                .ifExists(c -> c.traverse()
                        .limit(1)
                        .forEach(channelConfig -> {
                            getOrCreateChannel(channelConfig.key().name());
                        }));
        getConfig().get("mp.messaging.incoming")
                .ifExists(c -> c.traverse()
                        .limit(1)
                        .forEach(channelConfig -> {
                            getOrCreateChannel(channelConfig.key().name());
                        }));
    }

    ChannelProcessors getChannelProcessors(){
        return channelProcessors;
    }

    private void addIncomingConnector(Bean<?> bean) {
        OutgoingConnectorFactory outgoingConnectorFactory = lookup(bean, beanManager);
        String connectorName = bean.getBeanClass().getAnnotation(Connector.class).value();
        IncomingConnector incomingConnector = new IncomingConnector(connectorName, outgoingConnectorFactory, this);
        incomingConnectorMap.put(connectorName, incomingConnector);
    }

    void registerChannelProcessor(Bean<?> bean) {
        channelProcessorBeans.add(bean);
    }

    private void addChannelProcessor(Bean<?> bean) {
        MessagingChannelProcessor channelProcessor = lookup(bean, beanManager);
        channelProcessors.register(channelProcessor);
    }

    private void addOutgoingConnector(Bean<?> bean) {
        IncomingConnectorFactory incomingConnectorFactory = lookup(bean, beanManager);
        String connectorName = bean.getBeanClass().getAnnotation(Connector.class).value();
        OutgoingConnector outgoingConnector = new OutgoingConnector(connectorName, incomingConnectorFactory, this);
        outgoingConnectorMap.put(connectorName, outgoingConnector);
    }

    private void addIncomingMethod(AnnotatedMethod<?> m) {
        IncomingMethod incomingMethod = new IncomingMethod(m, errors);
        incomingMethod.validate();

        String channelName = incomingMethod.getIncomingChannelName();

        UniversalChannel universalChannel = getOrCreateChannel(channelName);
        universalChannel.setIncoming(incomingMethod);

        connectableBeanMethods.add(incomingMethod);
    }

    private void addOutgoingMethod(AnnotatedMethod<?> m) {
        OutgoingMethod outgoingMethod = new OutgoingMethod(m, errors);
        outgoingMethod.validate();

        String channelName = outgoingMethod.getOutgoingChannelName();

        UniversalChannel universalChannel = getOrCreateChannel(channelName);
        universalChannel.setOutgoing(outgoingMethod);

        connectableBeanMethods.add(outgoingMethod);
    }

    private void addProcessorMethod(AnnotatedMethod<?> m) {
        ProcessorMethod channelMethod = new ProcessorMethod(m, errors);
        channelMethod.validate();

        String incomingChannelName = channelMethod.getIncomingChannelName();
        String outgoingChannelName = channelMethod.getOutgoingChannelName();

        UniversalChannel incomingUniversalChannel = getOrCreateChannel(incomingChannelName);
        incomingUniversalChannel.setIncoming(channelMethod);

        UniversalChannel outgoingUniversalChannel = getOrCreateChannel(outgoingChannelName);
        outgoingUniversalChannel.setOutgoing(channelMethod);

        connectableBeanMethods.add(channelMethod);
    }

    UniversalChannel getOrCreateChannel(String channelName) {
        UniversalChannel universalChannel = channelMap.computeIfAbsent(channelName,
                n -> new UniversalChannel(n, this));
        return universalChannel;
    }

    @SuppressWarnings("unchecked")
    static <T> T lookup(Bean<?> bean, BeanManager beanManager) {
        jakarta.enterprise.context.spi.Context context = beanManager.getContext(bean.getScope());
        Object instance = context.get(bean);
        if (instance == null) {
            CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
            instance = beanManager.getReference(bean, bean.getBeanClass(), creationalContext);
        }
        if (instance == null) {
            throw new DeploymentException("Instance of bean " + bean.getName() + " not found");
        }
        return (T) instance;
    }
}
