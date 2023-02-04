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

import io.helidon.builder.Builder;
import io.helidon.builder.Singular;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Used for demonstrating and testing the Builder.
 */
@Builder(includeMetaAttributes = false)
public interface EdgeCases {

    /**
     * Demonstrates usage on an Optional return value that also has default values assigned.
     *
     * @return ignored, here for testing purposes only
     */
    @ConfiguredOption("test")
    Optional<String> optionalStringWithDefault();

    /**
     * Demonstrates usage on an Optional return value that also has default values assigned.
     *
     * @return ignored, here for testing purposes only
     */
    @ConfiguredOption("-1")
    Optional<Integer> optionalIntegerWithDefault();

    /**
     * Validates the conversion of ? to Object.
     *
     * @return ignored, here for testing purposes only
     */
    @Singular
    List<?> listOfObjects();

    /**
     * Validates the conversion of ? to Object.
     *
     * @return ignored, here for testing purposes only
     */
    @Singular
    Map<String, ? extends EdgeCases> mapOfEdgeCases();

}
