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

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessManagedBean;
import javax.enterprise.inject.spi.WithAnnotations;

import java.util.logging.Logger;

/**
 * Extension with partial implementation of MicroProfile Reactive Messaging Specification
 */
public class MessagingCdiExtension implements Extension {
    private static final Logger LOGGER = Logger.getLogger(MessagingCdiExtension.class.getName());

    private ChannelRouter channelRouter = new ChannelRouter();

    private void registerChannelMethods(@Observes @WithAnnotations({Incoming.class, Outgoing.class}) ProcessAnnotatedType<?> pat) {
        LOGGER.info("Registering incoming methods");
        pat.getAnnotatedType().getMethods()
                .stream()
                .filter(m -> m.isAnnotationPresent(Incoming.class))
                .forEach(m -> channelRouter.addIncomingMethod(m));

        LOGGER.info("Registering outgoing methods");
        pat.getAnnotatedType().getMethods()
                .stream()
                .filter(m -> m.isAnnotationPresent(Outgoing.class))
                .forEach(m -> channelRouter.addOutgoingMethod(m));
    }

    public void onProcessBean(@Observes ProcessManagedBean event) {
        LOGGER.info("Lookup connectors");
        // Lookup connectors
        Connector annotation = event.getAnnotatedBeanClass().getAnnotation(Connector.class);
        if (IncomingConnectorFactory.class.isAssignableFrom(event.getBean().getBeanClass()) && null != annotation) {
            channelRouter.addConnectorFactory(event.getBean());
        }
        LOGGER.info("Gather references");
        // Gather bean references
        //TODO: Multiple bean references(not singleton)
        channelRouter.registerBeanReference(event.getBean());
        LOGGER.info("References gathered");
    }

    public void makeConnections(@Observes AfterDeploymentValidation event, BeanManager beanManager) {
        LOGGER.info("Final connect");
        // Subscribe subscribers and publish publishers
        channelRouter.connect(beanManager);
        LOGGER.info("All connected");
    }

}
