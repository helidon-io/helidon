/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.test.testsubjects;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * A pickle. Demonstrates the use of enumerated types, optionals, and validation on builders.
 *
 * @see PickleBarrel
 */
@Prototype.Blueprint
interface PickleBlueprint {

    /**
     * The type of pickle is marked as required; which means that we cannot be build unless the type is defined.
     *
     * @return the type of pickle
     */
    @Option.Required
    PickleType type();

    /**
     * The size of the pickle (optionally defined).
     *
     * @return the size
     */
    Optional<PickleSize> size();

}
