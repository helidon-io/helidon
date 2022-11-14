/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.config.testsubjects;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.pico.config.api.ConfiguredBy;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;

@ConfiguredBy(SchemaRequiredConfig.class /** configured bean scope **/)
public class SchemaRequiredConfiguredService {

    private final SchemaRequiredConfig cfg;
    private final Optional<ContractA> aOptContract;
    private final Optional<ContractB> bOptContract;
    private final Provider<ASingletonConfiguredService> providerSingletonConfiguredService;
    private final Optional<ASingletonConfiguredService> optSingletonConfiguredService;
    private final List<Provider<MySimpleConfiguredService>> listOfProviderSimpleServices;
    private final AtomicInteger postConstructCallCount = new AtomicInteger();

    @Inject
    @Named("@default")
    Optional<ASingletonConfiguredService> optDefaultNamedSingleton;

    @Inject
    public SchemaRequiredConfiguredService(SchemaRequiredConfig cfg,
                                           Optional<ContractA> aOptContract,
                                           Optional<ContractB> bOptContract,
                                           Provider<ASingletonConfiguredService> providerSingletonConfiguredService,
                                           Optional<ASingletonConfiguredService> optSingletonConfiguredService,
                                           List<Provider<MySimpleConfiguredService>> listOfProviderSimpleServices
                                           ) {
        this.cfg = Objects.requireNonNull(cfg);
        this.aOptContract = aOptContract;
        this.bOptContract = bOptContract;
        this.providerSingletonConfiguredService = providerSingletonConfiguredService;
        this.optSingletonConfiguredService = optSingletonConfiguredService;
        this.listOfProviderSimpleServices = listOfProviderSimpleServices;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + cfg + "}";
    }

    @PostConstruct
    public void init() {
        postConstructCallCount.incrementAndGet();
        assert (1 == postConstructCallCount.get());
    }

    public int getActivationCount() {
        return postConstructCallCount.get();
    }

    public void reset() {
        postConstructCallCount.set(0);
    }

}
