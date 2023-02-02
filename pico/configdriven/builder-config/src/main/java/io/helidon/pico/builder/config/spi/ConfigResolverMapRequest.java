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

package io.helidon.pico.builder.config.spi;

import java.util.Optional;

import io.helidon.builder.Builder;

/**
 * An extension of {@link io.helidon.pico.builder.config.spi.ConfigResolverRequest} for handling {@link java.util.Map}-like
 * requests into {@link io.helidon.pico.builder.config.spi.ConfigResolver}.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
@Builder
public interface ConfigResolverMapRequest<K, V> extends ConfigResolverRequest<V> {

    /**
     * The key type of the map attribute.
     *
     * @return the key type of the map attribute
     */
    Class<K> keyType();

    /**
     * The generic component type of the {@link #keyType()}. For example, this would be "String" for
     * {@code Optional<String>}.
     *
     * @return the component type
     */
    Optional<Class<?>> keyComponentType();

}
