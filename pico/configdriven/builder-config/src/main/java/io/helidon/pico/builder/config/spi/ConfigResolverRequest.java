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
 * Used in conjunction with {@link io.helidon.pico.builder.config.spi.ConfigResolver}.
 *
 * @param <T> the attribute value type being resolved
 */
@Builder
public interface ConfigResolverRequest<T> {

    /**
     * The configuration key.
     *
     * @return the optional configuration key
     */
    String configKey();

    /**
     * The config builder attribute name - which is typically the same as the method name.
     *
     * @return the config builder attribute name
     */
    String attributeName();

    /**
     * The type of the attribute value.
     *
     * @return the type of the attribute value
     */
    Class<T> valueType();

    /**
     * The generic component type of the {@link #valueType()}. For example, this would be "String" for
     * {@code Optional<String>}.
     *
     * @return the value component type
     */
    Optional<Class<?>> valueComponentType();

}
