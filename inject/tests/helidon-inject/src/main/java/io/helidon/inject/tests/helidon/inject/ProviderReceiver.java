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

package io.helidon.inject.tests.helidon.inject;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.inject.service.Injection;

@Injection.Singleton
class ProviderReceiver {
    private final Supplier<NonSingletonService> provider;
    private final Supplier<List<NonSingletonService>> listOfProviders;
    private final Supplier<Optional<NonSingletonService>> optionalProvider;
    private final AContract contract;

    @Injection.Inject
    ProviderReceiver(Supplier<NonSingletonService> provider,
                     Supplier<List<NonSingletonService>> listOfProviders,
                     Supplier<Optional<NonSingletonService>> optionalProvider,
                     AContract contract) {
        this.provider = provider;
        this.listOfProviders = listOfProviders;
        this.optionalProvider = optionalProvider;
        this.contract = contract;
    }

    NonSingletonService nonSingletonService() {
        return provider.get();
    }

    List<NonSingletonService> listOfServices() {
        return listOfProviders.get();
    }

    Optional<NonSingletonService> optionalService() {
        return optionalProvider.get();
    }

    AContract contract() {
        return contract;
    }
}
