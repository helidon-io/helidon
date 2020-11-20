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
 */

package io.helidon.microprofile.messaging;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.DeploymentException;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessManagedBean;
import javax.enterprise.inject.spi.WithAnnotations;

import io.helidon.common.Errors;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;

import static javax.interceptor.Interceptor.Priority.PLATFORM_AFTER;

/**
 * MicroProfile Reactive Messaging CDI Extension.
 */
public class MessagingCdiExtension implements Extension {
    private static final Logger LOGGER = Logger.getLogger(MessagingCdiExtension.class.getName());

    private final ChannelRouter channelRouter = new ChannelRouter();

    /**
     * Get names of all channels accompanied by boolean if cancel or onError signal has been intercepted in it.
     *
     * @return map of channels
     */
    public Map<String, Boolean> channelsLiveness() {
        return channelRouter.getChannelMap()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().isLive().get()));
    }

    /**
     * Get names of all channels accompanied by boolean if onSubscribe signal has been intercepted in it.
     *
     * @return map of channels
     */
    public Map<String, Boolean> channelsReadiness() {
        return channelRouter.getChannelMap()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().isReady().get()));
    }

    public void registerMessagingMethodInvocationHook(BiConsumer<MessagingMethod, Message<?>> before,
                                                      BiConsumer<MessagingMethod, Object> after){
        channelRouter.addMethodHook(before, after);
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
        // Gather bean references
        channelRouter.registerBeanReference(event.getBean());
    }

    private void deploymentValidation(@Observes AfterDeploymentValidation event) {
        Errors.Collector errors = channelRouter.getErrors();
        boolean hasFatal = errors.hasFatal();
        Errors errorMessages = errors.collect();
        if (hasFatal) {
            throw new DeploymentException(errorMessages.toString());
        } else {
            errorMessages.log(LOGGER);
        }
    }

    private void makeConnections(@Observes @Priority(PLATFORM_AFTER + 101) @Initialized(ApplicationScoped.class) Object event,
                                 BeanManager beanManager) {
        // Subscribe subscribers, publish publishers and invoke "onAssembly" methods
        channelRouter.connect(beanManager);
    }

}
