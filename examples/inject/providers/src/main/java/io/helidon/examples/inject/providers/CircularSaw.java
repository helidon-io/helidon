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

import java.util.Optional;

import io.helidon.inject.service.Injection;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@Injection.RunLevel(Injection.RunLevel.STARTUP)
class CircularSaw implements Saw {

    private final Blade blade;

    @Inject
    CircularSaw(Optional<Blade> blade) {
        this.blade = blade.orElse(null);
    }

    @Override
    public String name() {
        return "Circular Saw: (blade=" + blade + ")";
    }

    @PostConstruct
    @SuppressWarnings("unused")
    void init() {
        System.out.println(name() + "; initialized");
    }

}
