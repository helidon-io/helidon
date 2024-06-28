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

package io.helidon.service.maven.plugin;

import java.util.Arrays;
import java.util.Set;

import io.helidon.codegen.Option;
import io.helidon.common.GenericType;
import io.helidon.common.types.TypeName;

final class ApplicationOptions {
    static final Option<PermittedProviderType> PERMITTED_PROVIDER_TYPE =
            Option.create("helidon.inject.app.permittedProviderType",
                          "Allowed level of non-deterministic providers, either of: "
                                  + Arrays.toString(PermittedProviderType.values()),
                          PermittedProviderType.NONE,
                          PermittedProviderType::valueOf,
                          GenericType.create(PermittedProviderType.class));
    static final Option<Set<TypeName>> PERMITTED_PROVIDER_TYPES =
            Option.createSet("helidon.inject.app.permittedProviderTypeNames",
                             "Fully qualified class names of providers that are permitted.",
                             Set.of(),
                             TypeName::create,
                             new GenericType<Set<TypeName>>() { });

    static final Option<Set<TypeName>> PERMITTED_PROVIDER_QUALIFIER_TYPES =
            Option.createSet("helidon.inject.app.permittedProviderQualifierTypeNames",
                             "Fully qualified class names of qualifiers of providers that are permitted.",
                             Set.of(),
                             TypeName::create,
                             new GenericType<Set<TypeName>>() { });

    private ApplicationOptions() {
    }
}
