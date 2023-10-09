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

package io.helidon.webserver.observe.spi;

import java.util.List;
import java.util.Optional;

import io.helidon.common.config.NamedService;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.ObserverConfigBase;

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
     * it with the routing.
     * <p>
     * If this method is used directly, it will NOT do the following (as this is handled by
     * {@link io.helidon.webserver.observe.ObserveFeature}):
     * <ul>
     *     <li>It will NOT register CORS</li>
     *     <li>It will NOT honor enabled configuration</li>
     * </ul>
     *
     * @param routing  routing builder
     * @param endpoint observer endpoint, combined with observability feature endpoint if needed
     */
    void register(HttpRouting.Builder routing,
                  String endpoint);

    default void register(NamedRoutingBuilder observabilityRouting,
                              List<NamedRoutingBuilder> routingBuilder,
                              String endpoint) {

    }


    interface ObserverRegistration {
        HttpRouting.Builder observerEndpoint();
        String observerSocketName();
        Optional<HttpRouting.Builder> namedSocket(String name);
        String endpoint(String defaultEndpoint);
    }
    record NamedRoutingBuilder(String name, HttpRouting.Builder builder) {

    }
}
