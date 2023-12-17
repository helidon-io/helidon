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

package io.helidon.inject.configdriven;

import java.util.List;
import java.util.Map;

import io.helidon.common.types.TypeName;
import io.helidon.inject.configdriven.service.NamedInstance;

/**
 * Manages the set of active {@link io.helidon.inject.configdriven.service.ConfigBean}'s,
 * along with whether the application is
 * configured to support dynamic aspects (i.e., dynamic in content, dynamic in lifecycle, etc.).
 */
public interface ConfigBeanRegistry {
    /**
     * Config bean registry instance for the current VM.
     *
     * @return config bean registry
     */
    static ConfigBeanRegistry instance() {
        return ConfigBeanRegistryImpl.CONFIG_BEAN_REGISTRY.get();
    }

    /**
     * The config bean registry is initialized as part of Helidon Injection's initialization, which happens when the service
     * registry is initialized and bound.
     *
     * @return true if the config bean registry has been initialized
     */
    boolean ready();

    /**
     * All active configuration beans (including default instances).
     *
     * @return map of all configuration beans, key is the config bean class, values are named instances
     */
    Map<TypeName, List<NamedInstance<?>>> allConfigBeans();
}
