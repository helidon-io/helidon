/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.config.spi;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Contract for resolving a configuration builder attribute element to the backing {@link io.helidon.common.config.Config}.
 */
public interface ConfigResolver {

    /**
     * Resolves a {@link io.helidon.builder.config.ConfigBean} singular element value from the
     * backing {@link io.helidon.common.config.Config}.
     *
     * @param ctx       the resolution context
     * @param meta      the meta attributes for this config bean
     * @param request   the request
     * @param <T> the attribute value type being resolved in the request
     * @return the resolved value or empty if unable to resolve the request
     */
    <T> Optional<T> of(ResolutionContext ctx,
                       Map<String, Map<String, Object>> meta,
                       ConfigResolverRequest<T> request);

    /**
     * Resolves a {@link io.helidon.builder.config.ConfigBean} collection-like element value from the
     * backing {@link io.helidon.common.config.Config}.
     *
     * @param ctx       the resolution context
     * @param meta      the meta attributes for this config bean
     * @param request   the request
     * @param <T> the attribute value type being resolved in the request
     * @return the resolved value or empty if unable to resolve the request
     */
    <T> Optional<Collection<T>> ofCollection(ResolutionContext ctx,
                                             Map<String, Map<String, Object>> meta,
                                             ConfigResolverRequest<T> request);

    /**
     * Resolves a {@link io.helidon.builder.config.ConfigBean} map-like element value from the
     * backing {@link io.helidon.common.config.Config}.
     *
     * @param ctx       the resolution context
     * @param meta      the meta attributes for this config bean
     * @param request   the request
     * @param <K> the map attribute key type being resolved in the request
     * @param <V> the map attribute value type being resolved in the request
     * @return the resolved value or empty if unable to resolve the request
     */
    <K, V> Optional<Map<K, V>> ofMap(ResolutionContext ctx,
                                     Map<String, Map<String, Object>> meta,
                                     ConfigResolverMapRequest<K, V> request);

}
