/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.arquillian;

import javax.enterprise.context.control.RequestContextController;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;

import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.test.spi.client.protocol.Protocol;
import org.jboss.arquillian.core.spi.LoadableExtension;
import org.jboss.arquillian.test.spi.TestEnricher;
import org.jboss.arquillian.testenricher.cdi.CDIInjectionEnricher;

/**
 * An arquillian LoadableExtension defining the {@link HelidonDeployableContainer}.
 */
class HelidonContainerExtension implements LoadableExtension {

    /**
     * We provide our own CDI enricher because the one in Arquillian
     * isn't properly initialized due to Helidon starting the CDI
     * container much later in time. Our enricher will grab the
     * current {@code BeanManager} and rely on the base class to
     * do the actual enrichment work.
     */
    static class HelidonCDIInjectionEnricher extends CDIInjectionEnricher {

        private BeanManager beanManager;
        private RequestContextController requestContextController;

        @Override
        public BeanManager getBeanManager() {
            if (beanManager == null) {
                CDI<Object> cdi = cdi();
                if (cdi != null) {
                    SeContainer container = (SeContainer) cdi;
                    if (container.isRunning()) {
                        beanManager = container.getBeanManager();
                    }
                }
            }
            return beanManager;
        }

        @Override
        public CreationalContext<Object> getCreationalContext() {
            return getBeanManager() != null ? getBeanManager().createCreationalContext(null) : null;
        }

        public RequestContextController getRequestContextController() {
            if (requestContextController == null) {
                CDI<Object> cdi = cdi();
                if (cdi != null) {
                    requestContextController = cdi.select(RequestContextController.class).get();
                }
            }
            return requestContextController;
        }

        private static CDI<Object> cdi() {
            try {
                return CDI.current();
            } catch (IllegalStateException ignored) {
                return null;
            }
        }
    }

    /**
     * The Helidon extension provides a container, a new protocol (even though
     * we run in embedded mode) and a new test enricher.
     *
     * @param builder Extension builder.
     */
    @Override
    public void register(ExtensionBuilder builder) {
        builder.service(DeployableContainer.class, HelidonDeployableContainer.class);
        builder.service(Protocol.class, HelidonLocalProtocol.class);
        builder.service(TestEnricher.class, HelidonCDIInjectionEnricher.class);
    }
}
