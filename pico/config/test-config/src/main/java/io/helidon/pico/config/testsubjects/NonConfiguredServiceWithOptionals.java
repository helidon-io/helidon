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
import java.util.Optional;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

// since this one does not use @ExternalContracts or @Contract, it will not be known as A or B here ...
// the lesser weight will lend itself to a configured service being selected over us...
@Weight(Weighted.DEFAULT_WEIGHT - 100)
@Singleton
public class NonConfiguredServiceWithOptionals implements ContractB {

    public Optional<MySimpleConfiguredService> optionalSimpleConfiguredService;
    public Optional<ASingletonConfiguredService> optionalSingletonConfiguredService;
    public static boolean ACTIVATED = false;

    @Inject
    public void setOptionalSimpleConfiguredService(Optional<MySimpleConfiguredService> provider) {
        this.optionalSimpleConfiguredService = Objects.requireNonNull(provider);
    }

    @Inject
    public void setOptionalSingletonConfiguredService(Optional<ASingletonConfiguredService> provider) {
        this.optionalSingletonConfiguredService = Objects.requireNonNull(provider);
    }

    @PostConstruct
    public void init() {
        assert (Objects.nonNull(optionalSimpleConfiguredService));
        assert (Objects.nonNull(optionalSingletonConfiguredService));
        NonConfiguredServiceWithOptionals.ACTIVATED = true;
    }

    @Override
    public MySimpleConfig getConfig() {
        return null;
    }

}
