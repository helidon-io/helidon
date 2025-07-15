/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.observe.health;

import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;
import io.helidon.health.HealthCheck;
import io.helidon.webserver.observe.ObserverConfigBase;
import io.helidon.webserver.observe.spi.ObserveProvider;

/**
 * Configuration of Health observer.
 *
 * @see io.helidon.webserver.observe.health.HealthObserver#create(HealthObserverConfig)
 * @see io.helidon.webserver.observe.health.HealthObserver#builder()
 */
@Prototype.Blueprint
@Prototype.Configured("health")
@Prototype.CustomMethods(HealthObserverSupport.CustomMethods.class)
@Prototype.Provides(ObserveProvider.class)
interface HealthObserverConfigBlueprint extends ObserverConfigBase, Prototype.Factory<HealthObserver> {
    @Option.Configured
    @Option.Default("health")
    String endpoint();

    @Override
    @Option.Default("health")
    String name();

    /**
     * Whether details should be printed.
     * By default, health only returns a {@link io.helidon.http.Status#NO_CONTENT_204} for success,
     * {@link io.helidon.http.Status#SERVICE_UNAVAILABLE_503} for health down,
     * and {@link io.helidon.http.Status#INTERNAL_SERVER_ERROR_500} in case of error with no entity.
     * When details are enabled, health returns {@link io.helidon.http.Status#OK_200} for success, same codes
     * otherwise
     * and a JSON entity with detailed information about each health check executed.
     *
     * @return set to {@code true} to enable details
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean details();

    /**
     * Health checks with implicit types.
     *
     * @return health checks to register with the observer
     */
    @Option.Singular("check")
    List<HealthCheck> healthChecks();

    /**
     * Whether to use services discovered by {@link java.util.ServiceLoader}.
     * By default, all {@link io.helidon.health.spi.HealthCheckProvider} based health checks are added.
     *
     * @return set to {@code false} to disable discovery
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean useSystemServices();

    /**
     * Config provided by the user (if any).
     *
     * @return configuration
     */
    Optional<Config> config();

    /**
     * Health check names to exclude in computing the overall health of the server.
     *
     * @return health check names to exclude
     */
    @Option.Configured
    @Option.Singular("excluded")
    List<String> exclude();

}
