/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.integrations.langchain4j;

import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;

/**
 * Helper methods to work with service registry.
 */
public class RegistryHelper {
    private RegistryHelper() {
    }

    /**
     * Get a named service instance from the registry.
     *
     * @param registry registry
     * @param clazz    contract to get
     * @param name     name of the instance
     * @param <T>      type of the instance
     * @return a named instance
     */
    public static <T> T named(ServiceRegistry registry, Class<T> clazz, String name) {
        if (Service.Named.DEFAULT_NAME.equals(name)) {
            return registry.get(clazz);
        } else {
            return registry.get(Lookup.builder()
                                        .addContract(clazz)
                                        .addQualifier(Qualifier.createNamed(name))
                                        .build());
        }
    }
}
