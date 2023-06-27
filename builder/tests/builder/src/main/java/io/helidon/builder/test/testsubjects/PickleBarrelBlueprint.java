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

import java.util.List;

import io.helidon.builder.api.Prototype;

/**
 * Demonstrates the builder using the singular pattern, enumerated types, and validation of required attributes.
 *
 * @see Container
 */
@Prototype.Blueprint
interface PickleBarrelBlueprint extends ContainerBlueprint {

    /**
     * The pickles in this barrel. The singular form of the builder will be "addPickle()".
     *
     * @return the pickles in this barrel
     */
    @Prototype.Singular
    List<Pickle> pickles();

}
