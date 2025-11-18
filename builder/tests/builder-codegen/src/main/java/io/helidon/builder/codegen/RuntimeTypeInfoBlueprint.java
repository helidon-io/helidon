/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.builder.codegen;

import java.util.Optional;

import io.helidon.builder.api.Prototype;

/**
 * Configuration specific to a factory method to create a runtime type from a prototype with a builder.
 */
@Prototype.Blueprint(detach = true)
interface RuntimeTypeInfoBlueprint {
    /**
     * Factory method.
     * If not defined, we expect the builder to build the correct type.
     *
     * @return the factory method if present
     */
    Optional<FactoryMethod> factoryMethod();

    /**
     * Builder information associated with this factory method.
     *
     * @return builder information
     */
    OptionBuilder optionBuilder();
}
