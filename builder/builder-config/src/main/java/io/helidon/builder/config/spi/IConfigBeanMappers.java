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

package io.helidon.builder.config.spi;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.common.config.Config;

/**
 * Loosely modeled from {@code io.helidon.config.spi.ConfigMapperProvider}.
 *
 * @see ConfigBeanMapper
 */
public interface IConfigBeanMappers {

/*
  Important Note: caution should be exercised to avoid any 0-arg or 1-arg method. This is because it might clash with generated
  methods. If its necessary to have a 0 or 1-arg method then the convention of prefixing the method with two underscores should be
  used.
 */

    /**
     * Returns a map of mapper functions associated with appropriate target type ({@code Class<?>}.
     *
     * @return a map of config mapper functions, never {@code null}
     */
    Map<Class<?>, Function<Config, ?>> __mappers();

    /**
     * A simple mapping function from config node to a typed value based on the expected class.
     *
     * @param type type of the expected mapping result
     * @param <T>  type returned from conversion
     * @return function to convert config node to the expected type, or empty if the type is not supported by this provider
     */
    @SuppressWarnings("unchecked")
    default <T> Optional<Function<Config, T>> __mapper(Class<T> type) {
        return Optional.ofNullable((Function<Config, T>) __mappers().get(type));
    }

}
