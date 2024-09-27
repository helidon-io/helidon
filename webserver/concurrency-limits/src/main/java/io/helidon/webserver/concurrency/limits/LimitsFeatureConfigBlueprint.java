/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.concurrency.limits;

import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.concurrency.limits.spi.LimitProvider;
import io.helidon.webserver.spi.ServerFeatureProvider;

@Prototype.Blueprint
@Prototype.Configured(value = LimitsFeature.ID, root = false)
@Prototype.Provides(ServerFeatureProvider.class)
interface LimitsFeatureConfigBlueprint extends Prototype.Factory<LimitsFeature> {
    /**
     * Weight of the context feature. As it is used by other features, the default is quite high:
     * {@value LimitsFeature#WEIGHT}.
     *
     * @return weight of the feature
     */
    @Option.DefaultDouble(LimitsFeature.WEIGHT)
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
    @Option.Default(LimitsFeature.ID)
    String name();

    /**
     * Concurrency limit to use to limit concurrent execution of incoming requests.
     * The default is to have unlimited concurrency.
     *
     * @return concurrency limit
     */
    @Option.Provider(value = LimitProvider.class, discoverServices = false)
    @Option.Configured
    Optional<Limit> limit();

    /**
     * Whether this feature is enabled, defaults to {@code true}.
     *
     * @return whether to enable this feature
     */
    @Option.DefaultBoolean(true)
    @Option.Configured
    boolean enabled();
}
