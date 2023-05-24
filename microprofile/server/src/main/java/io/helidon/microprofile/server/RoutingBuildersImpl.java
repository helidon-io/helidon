/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.server;

import io.helidon.webserver.Routing;

/**
 * Package-private implementation of the {@code RoutingBuilders} interface.
 */
class RoutingBuildersImpl implements RoutingBuilders {

    private final Routing.Builder defaultBuilder;
    private final Routing.Builder effectiveBuilder;

    RoutingBuildersImpl(Routing.Builder defaultBuilder, Routing.Builder effectiveBuilder) {
        this.defaultBuilder = defaultBuilder;
        this.effectiveBuilder = effectiveBuilder;
    }

    @Override
    public Routing.Builder defaultRoutingBuilder() {
        return defaultBuilder;
    }

    @Override
    public Routing.Builder routingBuilder() {
        return effectiveBuilder;
    }
}
