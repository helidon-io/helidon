/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.DeploymentException;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessManagedBean;
import javax.enterprise.inject.spi.WithAnnotations;

import io.helidon.microprofile.messaging.ChannelRouter;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;

/**
 * MicroProfile Reactive Messaging CDI Extension.
 */
public class MessagingCdiExtension implements Extension {
    private static final Logger LOGGER = Logger.getLogger(MessagingCdiExtension.class.getName());

    private ChannelRouter channelRouter = new ChannelRouter();
    private List<DeploymentException> deploymentExceptions = new ArrayList<>();

    private void registerChannelMethods(
            @Observes
            @WithAnnotations({Incoming.class, Outgoing.class}) ProcessAnnotatedType<?> pat) {
        // Lookup channel methods
        try {
            pat.getAnnotatedType().getMethods().forEach(m -> channelRouter.registerMethod(m));
        } catch (DeploymentException e) {
            deploymentExceptions.add(e);
        }
    }

    private void onProcessBean(@Observes ProcessManagedBean event) {
        // Lookup connectors
        if (null != event.getAnnotatedBeanClass().getAnnotation(Connector.class)) {
            channelRouter.registerConnectorFactory(event.getBean());
        }
        // Gather bean references
        channelRouter.registerBeanReference(event.getBean());
    }

    private void makeConnections(@Observes AfterDeploymentValidation event, BeanManager beanManager) {
        if (!deploymentExceptions.isEmpty()) {
            deploymentExceptions.stream()
                    .skip(1)
                    .forEach(thrown -> LOGGER.log(Level.SEVERE, thrown.getMessage(), thrown));
            throw deploymentExceptions.get(0);
        }
        LOGGER.info("Final connect");
        // Subscribe subscribers and publish publishers
        channelRouter.connect(beanManager);
        LOGGER.info("All connected");
    }

}
