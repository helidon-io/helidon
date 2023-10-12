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

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

import io.helidon.common.reactive.Multi;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.AfterDeploymentValidation;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.ProcessInjectionPoint;
import jakarta.enterprise.inject.spi.ProcessManagedBean;
import jakarta.enterprise.inject.spi.WithAnnotations;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Publisher;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_AFTER;
import static jakarta.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

/**
 * MicroProfile Reactive Messaging CDI Extension.
 */
public class MessagingCdiExtension implements Extension {

    private final ChannelRouter channelRouter = new ChannelRouter();
    private final Set<Consumer<AfterBeanDiscovery>> aotBeanRegistrations = new HashSet<>();

    /**
     * Initialize messaging CDI extension.
     */
    public MessagingCdiExtension() {
    }

    private void registerChannelMethods(
            @Observes
            @WithAnnotations({Incoming.class, Outgoing.class}) ProcessAnnotatedType<?> pat) {
        // Lookup channel methods
        pat.getAnnotatedType().getMethods().forEach(channelRouter::registerMethod);
    }

    private void onProcessBean(@Observes ProcessManagedBean<?> event) {
        // Lookup connectors
        if (null != event.getAnnotatedBeanClass().getAnnotation(Connector.class)) {
            channelRouter.registerConnectorFactory(event.getBean());
        }
        // Lookup channel processors
        if (MessagingChannelProcessor.class.isAssignableFrom(event.getAnnotatedBeanClass().getJavaClass())) {
            channelRouter.registerChannelProcessor(event.getBean());
        }
        // Gather bean references
        channelRouter.registerBeanReference(event.getBean());
    }

    private void deploymentValidation(@Observes AfterDeploymentValidation event) {
        channelRouter.validate();
    }

    <T extends Emitter<?>> void emitterInjectionPoints(@Observes ProcessInjectionPoint<?, T> pip) {
        String channelName = configureInternalChannel(pip);
        String fieldName = pip.getInjectionPoint().getMember().getName();
        // No @Channel annotation
        if (Objects.isNull(channelName)) return;

        OnOverflow onOverflow = Optional.ofNullable(pip.getInjectionPoint().getAnnotated().getAnnotation(OnOverflow.class))
                .orElse(Literals.defaultOnOverflow());

        OutgoingEmitter emitter = OutgoingEmitter.create(channelName, fieldName, onOverflow);

        aotBeanRegistrations.add(abd -> {
            channelRouter.registerEmitter(emitter);
            abd.addBean()
                    .addType(Literals.emitterType())
                    .beanClass(Emitter.class)
                    .addQualifiers(Literals.channel(emitter.getChannelName()), Literals.internalChannel(emitter.getChannelName()))
                    .scope(Dependent.class)
                    .createWith(instance -> emitter);
        });
    }

    <T extends Publisher<?>> void publisherInjectionPoints(@Observes ProcessInjectionPoint<?, T> pip) {
        String channelName = configureInternalChannel(pip);
        String fieldName = pip.getInjectionPoint().getMember().getName();
        // No @Channel annotation
        if (Objects.isNull(channelName)) return;

        Type type = pip.getInjectionPoint().getType();

        aotBeanRegistrations.add(abd -> {
            IncomingPublisher injectedPublisher =
                    new IncomingPublisher(channelName, fieldName, MessageUtils.hasGenericMessageType(type));
            channelRouter.registerPublisher(injectedPublisher);
            abd.addBean()
                    .addType(Literals.publisherType())
                    .beanClass(Publisher.class)
                    .addQualifiers(Literals.channel(channelName), Literals.internalChannel(channelName))
                    .scope(Dependent.class)
                    .createWith(instance -> injectedPublisher.getProcessor());
        });
    }

    <T extends PublisherBuilder<?>> void publisherBuilderInjectionPoints(@Observes ProcessInjectionPoint<?, T> pip) {
        String channelName = configureInternalChannel(pip);
        String fieldName = pip.getInjectionPoint().getMember().getName();
        // No @Channel annotation
        if (Objects.isNull(channelName)) return;

        Type type = pip.getInjectionPoint().getType();

        aotBeanRegistrations.add(abd -> {
            IncomingPublisher injectedPublisher =
                    new IncomingPublisher(channelName, fieldName, MessageUtils.hasGenericMessageType(type));
            channelRouter.registerPublisher(injectedPublisher);
            abd.addBean()
                    .addType(Literals.publisherBuilderType())
                    .beanClass(PublisherBuilder.class)
                    .addQualifiers(Literals.channel(channelName), Literals.internalChannel(channelName))
                    .scope(Dependent.class)
                    .createWith(instance -> ReactiveStreams.fromPublisher(injectedPublisher.getProcessor()));
        });
    }

    <T extends Flow.Publisher<?>> void multiInjectionPoints(@Observes ProcessInjectionPoint<?, T> pip) {
        String channelName = configureInternalChannel(pip);
        String fieldName = pip.getInjectionPoint().getMember().getName();
        // No @Channel annotation
        if (Objects.isNull(channelName)) return;

        Type type = pip.getInjectionPoint().getType();

        aotBeanRegistrations.add(abd -> {
            IncomingPublisher injectedPublisher =
                    new IncomingPublisher(channelName, fieldName, MessageUtils.hasGenericMessageType(type));
            channelRouter.registerPublisher(injectedPublisher);
            abd.addBean()
                    .addType(Literals.flowPublisherType())
                    .addType(Literals.multiType())
                    .beanClass(Multi.class)
                    .addQualifiers(Literals.channel(channelName), Literals.internalChannel(channelName))
                    .scope(Dependent.class)
                    .createWith(instance -> Multi.create(FlowAdapters.toFlowPublisher(injectedPublisher.getProcessor())));
        });
    }

    void afterBeanDiscovery(@Priority(PLATFORM_BEFORE + 10) @Observes final AfterBeanDiscovery event, BeanManager bm) {
        aotBeanRegistrations.forEach(abdConsumer -> abdConsumer.accept(event));
    }

    void makeConnections(@Observes @Priority(PLATFORM_AFTER + 101) @Initialized(ApplicationScoped.class) Object event,
                                 BeanManager beanManager) {
        // Subscribe subscribers, publish publishers and invoke "onAssembly" methods
        channelRouter.connect(beanManager);
    }

    private <T> String configureInternalChannel(ProcessInjectionPoint<?, T> pip) {
        return pip.getInjectionPoint().getQualifiers()
                .stream()
                .filter(a -> Channel.class.equals(a.annotationType()))
                .map(Channel.class::cast)
                .map(Channel::value)
                .peek(s -> {
                    // side effect -> adds internal annotation with proper qualifier
                    pip.configureInjectionPoint().addQualifier(Literals.internalChannel(s));
                })
                .findFirst()
                .orElse(null);
    }

}
