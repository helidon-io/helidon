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

import java.util.Objects;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

// since this one does not use @ExternalContracts or @Contract, it will not be known as A or B here ...
// the lesser weight will lend itself to a configured service being selected over us...
@Weight(Weighted.DEFAULT_WEIGHT - 100)
@Singleton
public class NonConfiguredServiceWithProviders implements ContractB {

    public Provider<MySimpleConfiguredService> providerSimpleConfiguredService;
    public Provider<ASingletonConfiguredService> providerSingletonConfiguredService;
    public static boolean ACTIVATED = false;

    @Inject
    public void setProviderSimpleConfiguredService(Provider<MySimpleConfiguredService> provider) {
        this.providerSimpleConfiguredService = Objects.requireNonNull(provider);
    }

    @Inject
    public void setProviderSingletonConfiguredService(Provider<ASingletonConfiguredService> provider) {
        this.providerSingletonConfiguredService = Objects.requireNonNull(provider);
    }

    @PostConstruct
    public void init() {
        assert (Objects.nonNull(providerSimpleConfiguredService));
        assert (Objects.nonNull(providerSingletonConfiguredService));
        NonConfiguredServiceWithProviders.ACTIVATED = true;
    }

    @Override
    public MySimpleConfig getConfig() {
        return null;
    }

}
