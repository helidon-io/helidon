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
 * Exercises alias bindings with implementation names that collide with generated state.
 */
@Data.Repository
@Data.Provider("jdbc")
public interface AliasBindingRepository {
    /**
     * Repeats a nullable input and binds the identifier after the replacement name.
     *
     * @param SQL_UPDATE The identifier is bound to the final physical position.
     * @param jdbcClient The replacement name supplies both repeated positions.
     * @return The affected row count is returned.
     */
    @Jdbc.Statement("UPDATE CONTACT SET NAME = :name, LABEL = :name WHERE ID = :id")
    @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
    long update(@Data.Param("id") long SQL_UPDATE, @Data.Param("name") String jdbcClient);
}
