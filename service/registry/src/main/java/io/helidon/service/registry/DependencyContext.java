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

package io.helidon.service.registry;

import java.util.Map;

/**
 * All data needed for creating an instance of a service.
 * <p>
 * The context contains only the services needed for the specific location, as provided by
 * {@link ServiceInfo#dependencies()}.
 */
public interface DependencyContext {
    /**
     * Create a new instance from a prepared map of dependencies.
     *
     * @param dependencies map of dependency to an instance
     * @return context to use for creating instances of services
     */
    static DependencyContext create(Map<Dependency, Object> dependencies) {
        return new DependencyContextImpl(dependencies);
    }

    /**
     * Obtain a parameter for a specific dependency.
     * The dependency must be known in advance and provided through
     * {@link ServiceInfo}.
     *
     * @param dependency the dependency metadata
     * @param <T>        type of the parameter, for convenience, the result is cast to this type
     * @return value for the parameter, this may be null if allowed
     */
    <T> T dependency(Dependency dependency);
}
