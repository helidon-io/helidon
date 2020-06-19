/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.webclient.jaxrs;

import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

import javax.annotation.Priority;
import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.FeatureContext;

import org.glassfish.jersey.client.ClientAsyncExecutor;
import org.glassfish.jersey.internal.spi.AutoDiscoverable;
import org.glassfish.jersey.model.internal.CommonConfig;
import org.glassfish.jersey.spi.ExecutorServiceProvider;

import static org.glassfish.jersey.CommonProperties.PROVIDER_DEFAULT_DISABLE;

/**
 * Auto discoverable feature to use a custom executor service
 * for all client asynchronous operations.
 * This is needed to support {@link io.helidon.common.context.Context} for
 * outbound calls.
 * Also disables default providers, unless configured by user.
 */
@ConstrainedTo(RuntimeType.CLIENT)
@Priority(121)
public class JerseyClientAutoDiscoverable implements AutoDiscoverable {
    private static final Logger LOGGER = Logger.getLogger(JerseyClientAutoDiscoverable.class.getName());

    @Override
    public void configure(FeatureContext context) {
        context.register(new EsProvider());
        Configuration jaxRsConfiguration = context.getConfiguration();
        if (jaxRsConfiguration instanceof CommonConfig) {
            // this should be always true, as we are in Jersey
            CommonConfig configuration = (CommonConfig) jaxRsConfiguration;

            Object property = configuration.getProperty(PROVIDER_DEFAULT_DISABLE);
            if (null == property) {
                LOGGER.fine("Disabling all Jersey default providers (DOM, SAX, Rendered Image, XML Source, and "
                                    + "XML Stream Source). You can enabled them by setting system property "
                                    + PROVIDER_DEFAULT_DISABLE + " to NONE");
                configuration.property(PROVIDER_DEFAULT_DISABLE, "ALL");
            } else if ("NONE".equals(property)) {
                configuration.property(PROVIDER_DEFAULT_DISABLE, null);
            }
        }
    }

    @ClientAsyncExecutor
    private static final class EsProvider implements ExecutorServiceProvider {
        @Override
        public ExecutorService getExecutorService() {
            return JaxRsClient.executor().get();
        }

        @Override
        public void dispose(ExecutorService executorService) {
            // no-op, as we use a shared executor instance
        }
    }
}
