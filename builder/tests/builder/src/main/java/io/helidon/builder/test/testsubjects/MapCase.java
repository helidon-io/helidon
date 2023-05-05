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

import io.helidon.builder.Builder;
import io.helidon.builder.Singular;

/**
 * Demonstrate singular properties of maps and builders.
 */
@Builder(implPrefix = "Test", implSuffix = "")
public abstract class MapCase {

    /**
     * For Testing.
     *
     * @return for testing
     */
    @Singular
    public abstract Map<String, String> stringToString();

    /**
     * For Testing.
     *
     * @return for testing
     */
    @Singular("Dependency")
    public abstract Map<String, Set<Dependency>> stringToDependencies();

    /**
     * For Testing.
     *
     * @return for testing
     */
    @Singular
    public abstract Map<String, Map<String, Dependency>> stringToDependencyMap();

    /**
     * For Testing.
     *
     * @return for testing
     */
    @Singular
    public abstract Map<String, List<String>> stringToStringList();


    /**
     * Test example only.
     */
    public interface Dependency {

        /**
         * Test example only.
         *
         * @return the name
         */
        String name();
    }

}
