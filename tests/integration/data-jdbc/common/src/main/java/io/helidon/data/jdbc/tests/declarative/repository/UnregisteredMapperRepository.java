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
import io.helidon.data.jdbc.tests.application.MissingContact;
import io.helidon.data.jdbc.tests.declarative.UnregisteredContactMapper;

/**
 * Repository used to verify missing explicit-mapper activation failure.
 */
@Data.Repository
@Data.Provider("jdbc")
public interface UnregisteredMapperRepository {

    /**
     * Returns one contact through an explicit mapper that is intentionally not registered.
     *
     * @param id contact identifier
     * @return mapped contact
     */
    @Jdbc.Statement("SELECT ID FROM CONTACT WHERE ID = :id")
    @Jdbc.RowMapper(UnregisteredContactMapper.class)
    MissingContact find(long id);
}
