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

package io.helidon.builder.test.testsubjects;

import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

@Prototype.Blueprint
@Configured
interface WithProviderBlueprint {
    @ConfiguredOption(provider = true, providerType = SomeProvider.class)
    SomeProvider.SomeService oneDiscover();
    @ConfiguredOption(provider = true, providerType = SomeProvider.class, providerDiscoverServices = false)
    SomeProvider.SomeService oneNotDiscover();
    @ConfiguredOption(provider = true, providerType = SomeProvider.class)
    Optional<SomeProvider.SomeService> optionalDiscover();
    @ConfiguredOption(provider = true, providerType = SomeProvider.class, providerDiscoverServices = false)
    Optional<SomeProvider.SomeService> optionalNotDiscover();
    @ConfiguredOption(provider = true, providerType = SomeProvider.class)
    List<SomeProvider.SomeService> listDiscover();
    @ConfiguredOption(provider = true, providerType = SomeProvider.class, providerDiscoverServices = false)
    List<SomeProvider.SomeService> listNotDiscover();
    /*
    The following should always be empty, as there are no implementations
     */
    @ConfiguredOption(provider = true, providerType = ProviderNoImpls.class)
    Optional<ProviderNoImpls.SomeService> optionalNoImplDiscover();
    @ConfiguredOption(provider = true, providerType = ProviderNoImpls.class, providerDiscoverServices = false)
    Optional<ProviderNoImpls.SomeService> optionalNoImplNotDiscover();
    @ConfiguredOption(provider = true, providerType = ProviderNoImpls.class)
    List<ProviderNoImpls.SomeService> listNoImplDiscover();
    @ConfiguredOption(provider = true, providerType = ProviderNoImpls.class, providerDiscoverServices = false)
    List<ProviderNoImpls.SomeService> listNoImplNotDiscover();
}
