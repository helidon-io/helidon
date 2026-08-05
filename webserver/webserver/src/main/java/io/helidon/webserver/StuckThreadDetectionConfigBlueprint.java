/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.time.Duration;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.webserver.spi.ServerFeatureProvider;

/**
 * Configuration of the stuck thread detection feature.
 */
@Prototype.Blueprint(decorator = StuckThreadDetectionConfigSupport.BuilderDecorator.class)
@Prototype.Configured(value = StuckThreadDetectionFeature.FEATURE_ID, root = false)
@Prototype.Provides(ServerFeatureProvider.class)
interface StuckThreadDetectionConfigBlueprint extends Prototype.Factory<StuckThreadDetectionFeature> {
    /**
     * Whether this feature is enabled.
     *
     * @return whether enabled, defaults to {@code true}
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Minimum time a request must be executing before its thread is reported as stuck.
     * The duration must be positive and representable in nanoseconds.
     *
     * @return stuck request threshold, defaults to 10 minutes
     */
    @Option.Configured
    @Option.Default("PT10M")
    Duration threshold();

    /**
     * Period between scans of executing request threads.
     * A request becomes eligible after {@link #threshold()} and is reported on the next scan.
     * The duration must be positive and representable in nanoseconds.
     *
     * @return check period, defaults to 1 minute
     */
    @Option.Configured
    @Option.Default("PT1M")
    Duration checkPeriod();

    /**
     * Weight of the feature.
     *
     * @return feature weight
     */
    @Option.Configured
    @Option.DefaultDouble(StuckThreadDetectionFeature.WEIGHT)
    double weight();

    /**
     * List of sockets to register this feature on. If empty, the feature is registered on all sockets.
     *
     * @return socket names to register on, defaults to empty (all available sockets)
     */
    @Option.Configured
    Set<String> sockets();

    /**
     * Name of this feature instance.
     *
     * @return instance name
     */
    @Option.Default(StuckThreadDetectionFeature.FEATURE_ID)
    String name();
}
