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
package io.helidon.tests.integration.data.jdbc.declarative.repository;

import java.util.Optional;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;
import io.helidon.tests.integration.data.jdbc.parameters.AliasStatements;
import io.helidon.tests.integration.data.jdbc.parameters.CharacterNameLookup;
import io.helidon.tests.integration.data.jdbc.parameters.SerializableNameLookup;

/**
 * Specializes a compiled generic contract and renames Java arguments while preserving aliases.
 */
@Data.Repository
@Data.Provider("jdbc")
public interface AliasRepository extends AliasStatements<String>, CharacterNameLookup<String>, SerializableNameLookup<String> {
    @Override
    @Jdbc.Statement("INSERT INTO CONTACT(NAME, EMAIL) VALUES (:rows, :email)")
    @Jdbc.GeneratedKeys("id")
    Optional<Long> aliasInsert(String value, String jdbcStatement);

    @Override
    @Jdbc.Statement("UPDATE CONTACT SET NAME = :name WHERE ID = :id")
    @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
    int aliasUpdate(long SQL_ALIAS_UPDATE, String jdbcStatement);

    @Override
    @Jdbc.Statement("SELECT NAME FROM CONTACT WHERE NAME = :key OR NAME = :key")
    String covariantName(String renamed);
}
