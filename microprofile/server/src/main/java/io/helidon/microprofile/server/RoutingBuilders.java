/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.microprofile.server;

import javax.enterprise.inject.spi.CDI;

import io.helidon.config.Config;
import io.helidon.webserver.Routing;

import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Provides {@link Routing.Builder} instances (for the default and the actual)
 * for a Helidon MP service, based on configuration for the component (if any)
 * and defaults otherwise.
 */
public interface RoutingBuilders {

    /**
     *
     * @return the default {@code Routing.Builder} for the component
     */
    Routing.Builder defaultRoutingBuilder();

    /**
     *
     * @return the actual {@code Routing.Builder} for the component; might be the default
     */
    Routing.Builder routingBuilder();

    /**
     * Prepares the default and actual {@link Routing.Builder} instances based
     * on the "routing" configuration for the specific component.
     *
     * @param componentName config key under which "routing" config might exist for the component of interest
     * @return {@code RoutingBuilders} based on the named config (or default)
     */
    static RoutingBuilders create(String componentName) {
        return create(((Config) ConfigProvider.getConfig()).get(componentName));
    }

    /**
     * Prepares the default and actual {@link Routing.Builder} instances based
     * on the "routing" configuration for the specific component configuration.
     *
     * @param componentConfig the configuration for the calling service
     * @return {@code RoutingBuilders} based on the config (or default)
     */
    static RoutingBuilders create(Config componentConfig) {
        return createFromRoutingName(componentConfig
                                             .get("routing")
                                             .asString()
                                             .orElse(null));
    }

    /**
     * Prepares the default and actual {@link Routing.Builder} instances based on a routing name.
     * If routing name is null or blank or {@code @default}, then the default routing will be used for the service
     * endpoint routing as well.
     *
     * @param routingName name of the routing to use for the service's endpoint routing
     * @return {@code RoutingBuilders} containing the default and service's routing
     */
    static RoutingBuilders createFromRoutingName(String routingName) {
        ServerCdiExtension extension = CDI.current().getBeanManager().getExtension(ServerCdiExtension.class);
        final Routing.Builder defaultRoutingBuilder = extension.serverRoutingBuilder();
        final Routing.Builder serviceRoutingBuilder =
                routingName == null || routingName.isBlank() || "@default".equals(routingName)
                        ? defaultRoutingBuilder
                        : extension.serverNamedRoutingBuilder(routingName);
        return new RoutingBuildersImpl(defaultRoutingBuilder, serviceRoutingBuilder);
    }

}
