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
package io.helidon.data.jdbc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Stores generated key configuration until a mapper is selected.
 */
final class JdbcGeneratedKeys implements JdbcClient.GeneratedKeys {

    private final JdbcStatement statement;
    private final List<String> columns = new ArrayList<>();

    // Reject only exact duplicates because case-distinct identifiers can designate different quoted database columns.
    private final Set<String> uniqueColumns = new HashSet<>();
    private boolean mapped;

    /**
     * Creates a generated-key configuration stage.
     *
     * @param statement owning statement
     */
    JdbcGeneratedKeys(JdbcStatement statement) {
        this.statement = statement;
    }

    /**
     * Adds one requested generated column.
     *
     * @param columnName generated column name
     * @return this stage
     */
    @Override
    public JdbcClient.GeneratedKeys addColumn(String columnName) {
        Objects.requireNonNull(columnName, "The generated column name must not be null.");
        ensureConfiguring();
        // Another terminal stage may already have claimed the shared statement.
        statement.ensureMutable();
        String validated = JdbcPreparationPlan.validateGeneratedColumn(columnName);
        if (!uniqueColumns.add(validated)) {
            throw new IllegalArgumentException("The generated column name '" + columnName + "' is duplicated.");
        }
        columns.add(columnName);
        return this;
    }

    /**
     * Selects generated-key row mapping and freezes column configuration.
     *
     * @param mapper generated-key mapper
     * @param <T> mapped type
     * @return mapped rows stage
     */
    @Override
    public <T> JdbcClient.Rows<T> map(JdbcClient.RowMapper<T> mapper) {
        Objects.requireNonNull(mapper, "The generated key mapper must not be null.");
        ensureConfiguring();
        statement.ensureMutable();
        JdbcPreparationPlan plan = JdbcPreparationPlan.generatedKeys(columns);
        mapped = true;
        return new JdbcRows<>(statement, mapper, plan);
    }

    /**
     * Rejects changes after mapping finalizes this stage.
     */
    private void ensureConfiguring() {
        if (mapped) {
            throw new IllegalStateException("Generated key mapping has already been selected.");
        }
    }
}
