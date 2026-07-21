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

package io.helidon.builder.test.testsubjects;

import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

@Prototype.Blueprint
@Prototype.Configured
interface ProviderPolicyBlueprint {
    @Option.Configured("type-only-auto")
    @Option.Provider(value = SomeProvider.class,
                     discoverServices = false,
                     identity = Option.Provider.Identity.TYPE_ONLY)
    Optional<List<SomeProvider.SomeService>> typeOnlyAuto();

    @Option.Configured("type-only-list")
    @Option.Provider(value = SomeProvider.class,
                     discoverServices = false,
                     identity = Option.Provider.Identity.TYPE_ONLY,
                     configForm = Option.Provider.ConfigForm.LIST)
    List<SomeProvider.SomeService> typeOnlyList();

    @Option.Configured("type-and-name-object")
    @Option.Provider(value = SomeProvider.class,
                     discoverServices = false,
                     configForm = Option.Provider.ConfigForm.OBJECT)
    List<SomeProvider.SomeService> typeAndNameObject();

    @Option.Configured("type-and-name-list")
    @Option.Provider(value = SomeProvider.class,
                     discoverServices = false,
                     configForm = Option.Provider.ConfigForm.LIST)
    List<SomeProvider.SomeService> typeAndNameList();

    @Option.Provider(value = ProviderNoImpls.SomeService.class,
                     discoverServices = false,
                     identity = Option.Provider.Identity.TYPE_ONLY,
                     configForm = Option.Provider.ConfigForm.LIST)
    List<ProviderNoImpls.SomeService> nonConfigured();
}
