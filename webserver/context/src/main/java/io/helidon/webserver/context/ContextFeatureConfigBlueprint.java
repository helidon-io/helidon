/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.context;

import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.webserver.spi.ServerFeatureProvider;

/**
 * Configuration of context feature.
 */
@Prototype.Blueprint
@Prototype.Configured(value = ContextFeature.CONTEXT_ID, root = false)
@Prototype.Provides(ServerFeatureProvider.class)
interface ContextFeatureConfigBlueprint extends Prototype.Factory<ContextFeature> {

    /**
     * Weight of the context feature. As it is used by other features, the default is quite high:
     * {@value io.helidon.webserver.context.ContextFeature#WEIGHT}.
     *
     * @return weight of the feature
     */
    @Option.DefaultDouble(ContextFeature.WEIGHT)
    @Option.Configured
    double weight();

    /**
     * List of sockets to register this feature on. If empty, it would get registered on all sockets.
     *
     * @return socket names to register on, defaults to empty (all available sockets)
     */
    @Option.Configured
    Set<String> sockets();

    /**
     * Name of this instance.
     *
     * @return instance name
     */
    @Option.Default(ContextFeature.CONTEXT_ID)
    String name();
}
