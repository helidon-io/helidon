/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.inject.api;

import java.util.List;
import java.util.Set;

import io.helidon.common.types.TypeName;

/**
 * Service metadata.
 */
public interface InjectServiceInfo extends io.helidon.service.registry.ServiceInfo {
    /**
     * List of injection points required by this service (and possibly by its supertypes).
     * Each dependency is a point of injection of one instance into
     * constructor, method parameter, or a field.
     *
     * @return required dependencies
     */
    @Override
    default List<Ip> dependencies() {
        return List.of();
    }

    /**
     * Service qualifiers.
     *
     * @return qualifiers
     */
    default Set<Qualifier> qualifiers() {
        return Set.of();
    }

    /**
     * Run level of this service.
     *
     * @return run level
     */
    default int runLevel() {
        return Injection.RunLevel.NORMAL;
    }

    /**
     * Scope of this service.
     *
     * @return scope of the service
     */
    TypeName scope();

    /**
     * Returns the instance of the core service descriptor.
     * As we use identity, this is a required method that MUST return the singleton instance of the service descriptor.
     *
     * @return singleton instance of the underlying service descriptor
     */
    default io.helidon.service.registry.ServiceInfo coreInfo() {
        // for all injection based service descriptors this is enough
        return this;
    }
}
