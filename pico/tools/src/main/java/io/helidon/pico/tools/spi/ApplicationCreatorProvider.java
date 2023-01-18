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

package io.helidon.pico.tools.spi;

import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.pico.tools.ApplicationCreator;

import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * Provides access to the global singleton {@link ApplicationCreatorProvider} in use.
 */
@Singleton
public class ApplicationCreatorProvider implements Provider<ApplicationCreator> {
    private static final LazyValue<ApplicationCreator> INSTANCE = LazyValue.create(ApplicationCreatorProvider::load);

    private static ApplicationCreator load() {
        return HelidonServiceLoader.create(ServiceLoader.load(ApplicationCreator.class, ApplicationCreator.class.getClassLoader()))
                .asList()
                .stream()
                .findFirst().orElseThrow();
    }

    // note that this is guaranteed to succeed since the default implementation is in this module
    @Override
    public ApplicationCreator get() {
        return INSTANCE.get();
    }

    /**
     * Returns the global instance that was service loaded. Note that this call is guaranteed to return a result since the
     * default implementation is here in this module.
     *
     * @return the global service instance with the highest weight
     */
    public static ApplicationCreator instance() {
        return INSTANCE.get();
    }

}
