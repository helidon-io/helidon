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

import java.util.Optional;

import io.helidon.common.config.Config;

/**
 * Maps a {@link io.helidon.common.config.Config} instance to a newly created
 * {@link io.helidon.builder.config.ConfigBean}-annotated type instance.
 */
@FunctionalInterface
public interface ConfigBeanMapper /* extends ConfigMapper<C> */ {

    /**
     * Translate the provided configuration into the appropriate config bean for this service type.
     *
     * @param config         the config
     * @param configBeanType the config bean type
     * @param <C> the config type
     * @param <T> the config bean type
     * @return the config bean generated, or empty if not possible to create
     */
    <C extends Config, T> Optional<T> toConfigBean(
            C config,
            Class<T> configBeanType);

}
