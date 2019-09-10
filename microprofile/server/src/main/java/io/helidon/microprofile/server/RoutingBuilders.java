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
 *
 */
package io.helidon.microprofile.server;

import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.microprofile.server.spi.MpServiceContext;
import io.helidon.webserver.Routing;

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
     * @param context the {@code MpServiceContext} for the calling service
     * @param componentName config key under which "routing" config might exist for the component of interest
     * @return {@code RoutingBuilders} based on the named config (or default)
     */
    static RoutingBuilders create(MpServiceContext context, String componentName) {
        return create(context, context.helidonConfig().get(componentName));
    }

    /**
     * Prepares the default and actual {@link Routing.Builder} instances based
     * on the "routing" configuration for the specific component configuration.
     *
     * @param context the {@code MpServiceContext} for the calling service
     * @param componentConfig the configuration for the calling service
     * @return {@code RoutingBuilders} based on the config (or default)
     */
    static RoutingBuilders create(MpServiceContext context, Config componentConfig) {
        final Routing.Builder defaultRoutingBuilder = context.serverRoutingBuilder();
        final Routing.Builder actualRoutingBuilder
                = componentConfig.get("routing")
                        .asString()
                        .flatMap(routeName -> {
                            // support for overriding the routing back to default port using config
                            if ("@default".equals(routeName)) {
                                return Optional.empty();
                            } else {
                                return Optional.of(routeName);
                            }
                        })
                        // use named routing
                        .map(context::serverNamedRoutingBuilder)
                        // use default server routing
                        .orElse(defaultRoutingBuilder);
        return new RoutingBuildersImpl(defaultRoutingBuilder, actualRoutingBuilder);
    }
}
