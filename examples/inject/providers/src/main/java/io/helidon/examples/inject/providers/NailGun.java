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

package io.helidon.examples.inject.providers;

import java.util.Objects;

import io.helidon.examples.inject.basics.Tool;
import io.helidon.inject.service.Injection;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
@Injection.RunLevel(Injection.RunLevel.STARTUP)
class NailGun implements Tool {

    private final Provider<Nail> nailProvider;

    @Inject
    NailGun(Provider<Nail> nailProvider) {
        this.nailProvider = Objects.requireNonNull(nailProvider);
    }

    @Override
    public String name() {
        return "Nail Gun: (nail provider=" + nailProvider + ")";
    }

    /**
     * This method will be called by Injection after this instance is lazily initialized (because this is the {@link PostConstruct}
     * method).
     */
    @PostConstruct
    @SuppressWarnings("unused")
    void init() {
        System.out.println(name() + "; initialized");
    }

}
