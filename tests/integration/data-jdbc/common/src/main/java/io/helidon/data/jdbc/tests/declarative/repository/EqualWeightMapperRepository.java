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
package io.helidon.data.jdbc.tests.declarative.repository;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;
import io.helidon.data.jdbc.tests.application.EqualWeightContact;

/**
 * Repository used to verify deterministic equal-weight mapper selection.
 */
@Data.Repository
@Data.Provider("jdbc")
public interface EqualWeightMapperRepository {

    /**
     * Returns one mapped contact name.
     *
     * @param id contact identifier
     * @return selected mapper result
     */
    @Jdbc.Statement("SELECT NAME FROM CONTACT WHERE ID = :id")
    @Jdbc.RowMapper
    EqualWeightContact find(long id);
}
