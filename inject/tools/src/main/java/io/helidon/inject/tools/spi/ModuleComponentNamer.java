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

package io.helidon.inject.tools.spi;

import java.util.Collection;
import java.util.Optional;

import io.helidon.common.types.TypeName;
import io.helidon.inject.api.ModuleComponent;

/**
 * Implementors of these are responsible for choosing the best {@link io.helidon.common.types.TypeName} for any
 * {@link ModuleComponent} being generated. Note that this provider will only be called if there is some
 * ambiguity in choosing a name (e.g., there are no exports or there is no {@code module-info} for the module being processed,
 * etc.)
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public interface ModuleComponentNamer {

    /**
     * Implementors should return the suggested {@link ModuleComponent} package name, or {@code empty}
     * to abstain from naming.
     *
     * @param serviceActivatorTypeNames the set of activator type names to be generated
     * @return the suggested package name for the component module, or empty to abstain from naming
     */
    Optional<String> suggestedPackageName(Collection<TypeName> serviceActivatorTypeNames);

}
