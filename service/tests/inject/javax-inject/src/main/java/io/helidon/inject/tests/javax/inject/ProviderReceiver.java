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

package io.helidon.inject.tests.javax.inject;

import java.util.List;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
class ProviderReceiver {
    private final Provider<NonSingletonService> provider;
    private final List<Provider<NonSingletonService>> listOfProviders;
    private final Optional<Provider<NonSingletonService>> optionalProvider;
    private final AContract contract;

    @Inject
    ProviderReceiver(Provider<NonSingletonService> provider,
                     List<Provider<NonSingletonService>> listOfProviders,
                     Optional<Provider<NonSingletonService>> optionalProvider,
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
        return listOfProviders.stream()
                .map(Provider::get)
                .toList();
    }

    Optional<NonSingletonService> optionalService() {
        return optionalProvider.map(Provider::get);
    }

    AContract contract() {
        return contract;
    }
}
