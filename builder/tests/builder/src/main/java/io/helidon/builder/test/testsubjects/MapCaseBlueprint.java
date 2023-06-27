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

package io.helidon.builder.test.testsubjects;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.builder.api.Prototype;

/**
 * Demonstrate singular properties of maps and builders.
 */
@Prototype.Blueprint
interface MapCaseBlueprint {

    /**
     * For Testing.
     *
     * @return for testing
     */
    @Prototype.Singular
    Map<String, String> stringToString();

    /**
     * For Testing.
     *
     * @return for testing
     */
    @Prototype.Singular("Dependency")
    Map<String, Set<Dependency>> stringToDependencies();

    /**
     * For Testing.
     *
     * @return for testing
     */
    @Prototype.Singular
    Map<String, Map<String, Dependency>> stringToDependencyMap();

    /**
     * For Testing.
     *
     * @return for testing
     */
    @Prototype.Singular
    Map<String, List<String>> stringToStringList();

    /**
     * Test example only.
     */
    interface Dependency {

        /**
         * Test example only.
         *
         * @return the name
         */
        String name();
    }

}
