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

import io.helidon.pico.ExternalContracts;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@ExternalContracts(value = World.class, moduleNames = "AnotherModuleMaybe")
@Named("unknown")
@Singleton
public class WorldImpl implements World, SomeOtherLocalNonContractInterface, Serializable {
    private final String name;

    WorldImpl() {
        this("unknown");
    }

    WorldImpl(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}
