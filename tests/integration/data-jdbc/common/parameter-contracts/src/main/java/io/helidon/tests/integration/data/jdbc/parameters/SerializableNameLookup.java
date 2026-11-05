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
package io.helidon.tests.integration.data.jdbc.parameters;

import java.io.Serializable;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;

/**
 * Supplies an inherited alias with a serializable return contract.
 *
 * @param <T> The concrete repository supplies the input type.
 */
public interface SerializableNameLookup<T> {
    /**
     * Looks up a contact name using repeated aliases.
     *
     * @param original The contact name supplies both markers.
     * @return The matching name is returned.
     */
    @Jdbc.Statement("SELECT NAME FROM CONTACT WHERE NAME = :key OR NAME = :key")
    Serializable covariantName(@Data.Param("key") T original);
}
