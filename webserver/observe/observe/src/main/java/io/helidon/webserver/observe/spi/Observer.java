/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.observe.spi;

import java.util.List;
import java.util.function.UnaryOperator;

import io.helidon.common.config.NamedService;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.ObserverConfigBase;
import io.helidon.webserver.spi.ServerFeature;

/**
 * An observer adds observability feature to Helidon {@link io.helidon.webserver.observe.ObserveFeature},
 * such as health, metrics.
 * <p>
 * An observer may register features, services, filters on routing, or do some other magic that is related to
 * observability. Some observers may expose endpoints, some may use a push approach.
 * <p>
 * Configuration of an observer is located under {@code observe.observer} root configuration node, with
 * {@link io.helidon.webserver.observe.spi.ObserveProvider#configKey()} key below it.
 */
public interface Observer extends NamedService {
    /**
     * Configuration of this observer.
     *
     * @return configuration of the observer
     */
    ObserverConfigBase prototype();

    /**
     * Type of this observer, to make sure we do not configure an observer both from {@link java.util.ServiceLoader} and
     * using programmatic approach.
     * If it is desired to have more than one observer of the same type, always use programmatic approach
     *
     * @return type of this observer, should match {@link io.helidon.webserver.observe.spi.ObserveProvider#type()}
     */
    @Override
    String type();

    @Override
    default String name() {
        return prototype().name();
    }

    /**
     * Register the observer features, services, and/or filters.
     * This is used by the observe feature.
     * Do NOT use this method directly, kindly start with {@link io.helidon.webserver.observe.ObserveFeature} and register
     * it with the server builder.
     * <p>
     * Implementations of observers should register all they need through an {@link io.helidon.webserver.http.HttpFeature},
     * to make sure the weight of the {@link io.helidon.webserver.observe.ObserveFeature} is honored when routing is
     * set up. If you do not use an HttpFeature, the registration will always happen later than the default (business) routing
     * feature.
     *
     * @param featureContext         access to all routing builders, for cases where this observer needs to register additional
     *                               components to other sockets
     * @param observeEndpointRouting routing builders that expose observability endpoints, and this feature must use these to
     *                               register any endpoints exposed for observability
     * @param endpointFunction       function to obtain the final endpoint for observers that expose observe endpoints (such as
     *                               {@code /observe/health} would be provider for {@code health} by default)
     */
    void register(ServerFeature.ServerFeatureContext featureContext,
                  List<HttpRouting.Builder> observeEndpointRouting,
                  UnaryOperator<String> endpointFunction);
}
