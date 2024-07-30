/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.spi;

import io.helidon.inject.api.Bootstrap;
import io.helidon.inject.api.InjectionServices;

/**
 * Java {@link java.util.ServiceLoader} provider interface to find implementation of {@link InjectionServices}.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public interface InjectionServicesProvider {

    /**
     * Provide the {@code Injection} Services implementation given the provided primordial {@link Bootstrap}
     * configuration instance.
     *
     * @param bootstrap the primordial bootstrap configuration
     * @return services instance configured with the provided bootstrap instance
     */
    InjectionServices services(Bootstrap bootstrap);

}
