/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.mapper.Mappers;

@Prototype.Blueprint
@Prototype.Configured
@Prototype.RegistrySupport
interface WithProviderRegistryBlueprint {
    @Option.Configured
    @Option.Provider(SomeProvider.class)
    SomeProvider.SomeService oneDiscover();

    @Option.Configured
    @Option.Provider(value = SomeProvider.class, discoverServices = false)
    SomeProvider.SomeService oneNotDiscover();

    @Option.Configured
    @Option.Provider(SomeProvider.class)
    Optional<SomeProvider.SomeService> optionalDiscover();

    @Option.Configured
    @Option.Provider(value = SomeProvider.class, discoverServices = false)
    Optional<SomeProvider.SomeService> optionalNotDiscover();

    @Option.Configured
    @Option.Provider(SomeProvider.class)
    List<SomeProvider.SomeService> listDiscover();

    @Option.Configured
    @Option.Provider(value = SomeProvider.class, discoverServices = false)
    List<SomeProvider.SomeService> listNotDiscover();

    /*
    The following should always be empty, as there are no implementations
     */
    @Option.Configured
    @Option.Provider(ProviderNoImpls.class)
    Optional<ProviderNoImpls.SomeService> optionalNoImplDiscover();

    @Option.Configured
    @Option.Provider(value = ProviderNoImpls.class, discoverServices = false)
    Optional<ProviderNoImpls.SomeService> optionalNoImplNotDiscover();

    @Option.Configured
    @Option.Provider(ProviderNoImpls.class)
    List<ProviderNoImpls.SomeService> listNoImplDiscover();

    @Option.Access("")
    @Option.Provider(ProviderNoImpls.SomeService.class)
    List<ProviderNoImpls.SomeService> listNoImplDiscoverNoConfig();

    @Option.Provider(ProviderNoImpls.SomeService.class)
    Optional<ProviderNoImpls.SomeService> noImplDiscoverNoConfig();

    @Option.Configured
    @Option.Provider(value = ProviderNoImpls.class, discoverServices = false)
    List<ProviderNoImpls.SomeService> listNoImplNotDiscover();

    @Option.RegistryService
    Optional<Mappers> mappers();

    @Option.RegistryService
    Mappers mappersExplicit();

    @Option.RegistryService
    List<Mappers> mappersList();

    @Option.RegistryService
    Set<Mappers> mappersSet();
}
