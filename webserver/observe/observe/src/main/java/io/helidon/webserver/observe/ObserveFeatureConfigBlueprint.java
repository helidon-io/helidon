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

package io.helidon.webserver.observe;

import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;
import io.helidon.cors.CrossOriginConfig;
import io.helidon.webserver.observe.spi.ObserveProvider;
import io.helidon.webserver.observe.spi.Observer;
import io.helidon.webserver.spi.ServerFeatureProvider;

/**
 * Configuration for observability feature itself.
 */
@Prototype.Blueprint
@Prototype.Configured(value = ObserveFeature.OBSERVE_ID, root = false)
@Prototype.Provides(ServerFeatureProvider.class)
interface ObserveFeatureConfigBlueprint extends Prototype.Factory<ObserveFeature> {

    /**
     * Cors support inherited by each observe provider, unless explicitly configured.
     *
     * @return cors support to use
     */
    @Option.Configured
    @Option.DefaultCode("@io.helidon.cors.CrossOriginConfig@.create()")
    CrossOriginConfig cors();

    /**
     * Whether the observe support is enabled.
     *
     * @return {@code false} to disable observe feature
     */
    @Option.DefaultBoolean(true)
    @Option.Configured
    boolean enabled();

    /**
     * Root endpoint to use for observe providers. By default, all observe endpoint are under this root endpoint.
     * <p>
     * Example:
     * <br>
     * If root endpoint is {@code /observe} (the default), and default health endpoint is {@code health} (relative),
     * health endpoint would be {@code /observe/health}.
     *
     * @return endpoint to use
     */
    @Option.Default("/observe")
    @Option.Configured
    String endpoint();

    /**
     * Change the weight of this feature. This may change the order of registration of this feature.
     * By default, observability weight is {@value ObserveFeature#WEIGHT} so it is registered after routing.
     *
     * @return weight to use
     */
    @Option.DefaultDouble(ObserveFeature.WEIGHT)
    @Option.Configured
    double weight();

    /**
     * Observers to use with this observe features.
     * Each observer type is registered only once, unless it uses a custom name (default name is the same as the type).
     *
     * @return list of observers to use in this feature
     */
    @Option.Singular
    @Option.Configured
    @Option.Provider(ObserveProvider.class)
    List<Observer> observers();

    /**
     * Configuration of the observe feature, if present.
     *
     * @return config node of the feature
     */
    Optional<Config> config();

    /**
     * Sockets the observability endpoint should be exposed on. If not defined, defaults to the default socket
     * ({@value io.helidon.webserver.WebServer#DEFAULT_SOCKET_NAME}.
     * Each observer may have its own configuration of sockets that are relevant to it, this only controls the endpoints!
     *
     * @return list of sockets to register observe endpoint on
     */
    @Option.Configured
    List<String> sockets();

    /**
     * Name of this instance.
     *
     * @return instance name
     */
    @Option.Default(ObserveFeature.OBSERVE_ID)
    String name();
}
