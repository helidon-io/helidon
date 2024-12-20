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

package io.helidon.service.codegen.spi;

import java.util.Set;

import io.helidon.codegen.Option;
import io.helidon.service.codegen.RegistryCodegenContext;

/**
 * A {@link java.util.ServiceLoader} provider interface for observers that will be
 * called for code generation events.
 */
public interface InjectCodegenObserverProvider {
    /**
     * The provider can add supported options.
     *
     * @return options supported by this provider
     */
    default Set<Option<?>> supportedOptions() {
        return Set.of();
    }

    /**
     * Create a new observer based on the Helidon Inject code generation context.
     *
     * @param context code generation context for this code generation session
     * @return a new observer
     */
    InjectCodegenObserver create(RegistryCodegenContext context);
}
