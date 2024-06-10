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

package io.helidon.inject.tools;

import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.inject.tools.spi.ActivatorCreator;

import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * Provides access to the global singleton {@link ActivatorCreator} in use.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
@Singleton
public class ActivatorCreatorProvider implements Provider<ActivatorCreator> {
    private static final LazyValue<ActivatorCreator> INSTANCE = LazyValue.create(ActivatorCreatorProvider::load);

    /**
     * Service loader based constructor.
     *
     * @deprecated this is a Java ServiceLoader implementation and the constructor should not be used directly
     */
    @Deprecated
    public ActivatorCreatorProvider() {
    }

    private static ActivatorCreator load() {
        return HelidonServiceLoader.create(ServiceLoader.load(ActivatorCreator.class, ActivatorCreator.class.getClassLoader()))
                .asList()
                .stream()
                .findFirst().orElseThrow();
    }

    // note that this is guaranteed to succeed since the default implementation is in this module
    @Override
    public ActivatorCreator get() {
        return INSTANCE.get();
    }

    /**
     * Returns the global instance that was service loaded. Note that this call is guaranteed to return a result since the
     * default implementation is here in this module.
     *
     * @return the global service instance with the highest weight
     */
    public static ActivatorCreator instance() {
        return INSTANCE.get();
    }

}
