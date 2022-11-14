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

package io.helidon.pico.testsubjects.hello;

import java.io.Serializable;
import java.util.Optional;

import io.helidon.pico.DefaultQualifierAndValue;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.Module;
import io.helidon.pico.ServiceBinder;
import io.helidon.pico.testsupport.BasicSingletonServiceProvider;

import jakarta.inject.Singleton;

/**
 * Example for how we can programmatically add a {@link io.helidon.pico.Module}.
 */
@Singleton
public class MyCustomModule implements Module {

    @Override
    public void configure(ServiceBinder binder) {
        binder.bind(BasicSingletonServiceProvider.createBasicServiceProvider(WorldImpl.class, DefaultServiceInfo.builder()
                        .serviceTypeName(WorldImpl.class.getName())
                        .qualifier(DefaultQualifierAndValue.createNamed("unknown"))
                        .contractsImplemented(binder.toContractNames(World.class, SomeOtherLocalNonContractInterface.class, Serializable.class))
                .build()));
    }

    @Override
    public Optional<String> name() {
        return Optional.of(getClass().getSimpleName());
    }

}
