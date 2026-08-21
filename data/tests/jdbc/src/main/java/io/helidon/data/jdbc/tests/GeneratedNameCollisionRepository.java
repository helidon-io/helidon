/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.data.jdbc.tests;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;

/**
 * Generated-source fixture for overloaded, inherited, and normalization-colliding method names.
 */
@Data.Repository
@Data.Provider("jdbc")
public interface GeneratedNameCollisionRepository extends GeneratedNameCollisionParent {

    /**
     * First overload using a reference parameter.
     *
     * @param value query value
     * @return mapped value
     */
    @Jdbc.Statement("SELECT ?")
    String findValue(String value);

    /**
     * Second overload using a primitive parameter.
     *
     * @param value query value
     * @return mapped value
     */
    @Jdbc.Statement("SELECT ?")
    String findValue(long value);

    /**
     * Distinct Java name that normalizes to the first overload's constant name.
     *
     * @param value query value
     * @return mapped value
     */
    @Jdbc.Statement("SELECT ?")
    String find_Value(int value);

    /**
     * Child declaration that overloads a method inherited from the parent interface.
     *
     * @param value query value
     * @return mapped value
     */
    @Jdbc.Statement("SELECT ?")
    String inheritedValue(String value);
}

/**
 * Parent declaration used to prove inherited overloads share the repository naming scope.
 */
interface GeneratedNameCollisionParent {

    /**
     * Parent overload inherited by the generated repository.
     *
     * @param value query value
     * @return mapped value
     */
    @Jdbc.Statement("SELECT ?")
    String inheritedValue(long value);
}
