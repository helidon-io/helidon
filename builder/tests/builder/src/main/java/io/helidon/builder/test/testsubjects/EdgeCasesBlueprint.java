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
import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Used for demonstrating and testing the Builder.
 */
@Prototype.Blueprint
interface EdgeCasesBlueprint {

    /**
     * Demonstrates usage on an Optional return value that also has default values assigned.
     *
     * @return ignored, here for testing purposes only
     */
    @Option.Default("test")
    Optional<String> optionalStringWithDefault();

    /**
     * Demonstrates usage on an Optional return value that also has default values assigned.
     *
     * @return ignored, here for testing purposes only
     */
    @Option.DefaultInt(-1)
    Optional<Integer> optionalIntegerWithDefault();

    /**
     * Validates the conversion of ? to Object.
     *
     * @return ignored, here for testing purposes only
     */
    @Option.Singular
    List<Object> listOfObjects();

    /**
     * Validates the conversion of ? to Object.
     *
     * @return ignored, here for testing purposes only
     */
    @Option.Singular
    Map<String, EdgeCases> mapOfEdgeCases();

}
