/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.common.config.spi;

import io.helidon.common.config.Config;
import io.helidon.common.config.ConfigException;

/**
 * Config mapper is provided to {@link ConfigMapperProvider} to help transformation of
 * complex structures.
 *
 * @param <C> the Config type
 */
@FunctionalInterface
public interface ConfigMapper<C extends Config> {

    /**
     * Converts the specified {@code Config} node to the target type.
     *
     * @param config config node to be transformed
     * @param type   type to which the config node is to be transformed
     * @param <T>    type to which the config node is to be transformed
     * @return transformed value of type {@code T}; never returns {@code null}
     * @throws io.helidon.common.config.ConfigException if any issues occur in mapping
     */
    <T> T map(C config, Class<T> type) throws ConfigException;

}
