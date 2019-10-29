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
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessManagedBean;
import javax.enterprise.inject.spi.WithAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Extension with partial implementation of MicroProfile Reactive Messaging Specification
 */
public class MessagingCdiExtension implements Extension {
    private static final Logger LOGGER = Logger.getLogger(MessagingCdiExtension.class.getName());

    public List<IncomingSubscriber> incomingSubscribers = new ArrayList<>();

    public Map<String, Bean<?>> incomingConnectorFactoryMap = new HashMap<>();

    private void registerIncomings(@Observes @WithAnnotations({Incoming.class}) ProcessAnnotatedType<?> pat) {
        LOGGER.info("Registering incoming methods");
        pat.getAnnotatedType().getMethods().forEach(m -> incomingSubscribers.add(new IncomingSubscriber(m, incomingConnectorFactoryMap)));
    }

    public void onProcessBean(@Observes ProcessManagedBean event) {
        Class beanType = event.getBean().getBeanClass();
        // Lookup connectors
        Connector annotation = event.getAnnotatedBeanClass().getAnnotation(Connector.class);
        if (IncomingConnectorFactory.class.isAssignableFrom(beanType) && null != annotation) {
            incomingConnectorFactoryMap.put(annotation.value(), event.getBean());
        }
        // Gather bean references
        incomingSubscribers.stream()
                .filter(m -> m.getDeclaringType() == beanType)
                .forEach(m -> m.setDeclaringBean(event.getBean()));
    }

    public void onAfterDeploymentValidation(@Observes AfterDeploymentValidation event, BeanManager beanManager) {
        Config config = ((MpConfig) ConfigProvider.getConfig()).helidonConfig();
        incomingSubscribers.forEach(m -> m.subscribe(beanManager, config));
    }

}
