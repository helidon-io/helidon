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

package io.helidon.inject.configdriven.api;

import java.util.List;

import io.helidon.common.config.Config;

/**
 * Used from generated code.
 * Represents the required information to handle config beans, either from {@link ConfigBean}
 * annotation, or from other means.
 *
 * @param <T> type of the config bean
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public interface ConfigBeanFactory<T> {
    /**
     * Create instances from configuration.
     *
     * @param config configuration to use (root configuration instance)
     * @return list of config bean instances
     */
    List<NamedInstance<T>> createConfigBeans(Config config);

    /**
     * Type of config bean.
     *
     * @return bean type
     */
    Class<T> configBeanType();

}
