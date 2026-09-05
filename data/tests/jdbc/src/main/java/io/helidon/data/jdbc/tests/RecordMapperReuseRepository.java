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

import java.util.List;
import java.util.Optional;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;

/**
 * Generated-source fixture for reuse of implicit record mappers.
 */
@Data.Repository
@Data.Provider("jdbc")
public interface RecordMapperReuseRepository {

    /**
     * Returns the first string projection.
     *
     * @return mapped projection
     */
    @Jdbc.Statement("SELECT VALUE FROM TEST_VALUE")
    Projection<String> stringValue();

    /**
     * Returns the same projection type with different cardinality.
     *
     * @return optional mapped projection
     */
    @Jdbc.Statement("SELECT VALUE FROM TEST_VALUE")
    Optional<Projection<String>> optionalStringValue();

    /**
     * Returns a structurally distinct parameterization of the same record declaration.
     *
     * @return mapped projections
     */
    @Jdbc.Statement("SELECT VALUE FROM TEST_VALUE")
    List<Projection<Integer>> integerValues();

    /**
     * Record projection whose component type is supplied by each mapped use site.
     *
     * @param value projected value
     * @param <T> projection identity type
     */
    record Projection<T>(T value) {
    }
}
