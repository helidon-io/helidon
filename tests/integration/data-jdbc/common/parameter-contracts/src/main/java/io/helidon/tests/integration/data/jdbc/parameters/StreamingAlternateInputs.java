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

import java.util.function.Consumer;
import java.util.stream.Stream;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;

/**
 * Declares the same streaming contract through an unrelated generic parent.
 *
 * @param <T> The concrete repository supplies the mapped row type.
 */
public interface StreamingAlternateInputs<T> {
    /**
     * Streams ordered values using the same alias with different Java parameter names.
     *
     * @param statement The alias supplies the SQL binding name.
     * @param rows The consumer processes detached values.
     */
    @Jdbc.Statement("SELECT DATA_VALUE FROM ST_VALUE WHERE :value IS NULL OR DATA_VALUE = :value ORDER BY ID")
    void values(@Data.Param("value") T statement, Consumer<Stream<T>> rows);
}
