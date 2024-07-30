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

package io.helidon.inject.runtime;

import io.helidon.inject.api.Helidon;
import io.helidon.spi.HelidonStartupProvider;

/**
 * Service provider implementation, should only be used from {@link java.util.ServiceLoader}.
 * See {@link Helidon} type to discover programmatic API.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public class HelidonInjectionStartupProvider implements HelidonStartupProvider {
    /**
     * Required default constructor needed for {@link java.util.ServiceLoader}.
     *
     * @deprecated please do not use directly
     */
    @Deprecated
    public HelidonInjectionStartupProvider() {
    }

    @Override
    public void start(String[] arguments) {
        Helidon.start();
    }
}
