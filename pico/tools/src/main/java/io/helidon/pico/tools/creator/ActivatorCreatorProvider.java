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

package io.helidon.pico.tools.creator;

import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;

import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * Provides access to the {@link io.helidon.pico.tools.creator.ActivatorCreator} in use.
 */
@Singleton
public class ActivatorCreatorProvider implements Provider<ActivatorCreator> {
    private static final LazyValue<ActivatorCreator> INSTANCE = LazyValue.create(ActivatorCreatorProvider::load);

    private static ActivatorCreator load() {
        return HelidonServiceLoader.create(ServiceLoader.load(ActivatorCreator.class, ActivatorCreator.class.getClassLoader()))
                .asList()
                .stream()
                .findFirst().orElseThrow();
    }

    @Override
    public ActivatorCreator get() {
        return getInstance();
    }

    /**
     * @return The global {@link io.helidon.pico.tools.creator.ActivatorCreator} instance.
     */
    public static ActivatorCreator getInstance() {
        return INSTANCE.get();
    }
}
