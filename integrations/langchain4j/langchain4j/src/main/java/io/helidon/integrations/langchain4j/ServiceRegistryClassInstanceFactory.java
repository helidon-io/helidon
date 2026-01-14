/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import io.helidon.service.registry.Services;

import dev.langchain4j.spi.classloading.ClassInstanceFactory;

/**
 * LangChain4j ClassInstanceFactory implementation
 * that retrieves instances of classes from Helidon global
 * {@link Services} registry.
 *
 * @see ClassInstanceFactory
 * @see Services
 */
public class ServiceRegistryClassInstanceFactory implements ClassInstanceFactory {

    /**
     * Constructs a new {@code ServiceRegistryClassInstanceFactory}.
     */
    public ServiceRegistryClassInstanceFactory() {
    }

    /**
     * Retrieves an instance of the given class from the Helidon {@link Services} registry.
     *
     * @param <T>   the type of the class
     * @param clazz the class to retrieve an instance of
     * @return an instance of the requested class, or {@code null} if not found
     */
    @Override
    public <T> T getInstanceOfClass(Class<T> clazz) {
        return Services.first(clazz).orElse(null);
    }
}
