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
import java.util.Set;

/**
 * Immutable statement preparation and primary result contract.
 *
 * @param resultKind expected primary result kind
 * @param generatedColumns generated columns, empty when generated keys are not requested
 */
record JdbcPreparationPlan(ResultKind resultKind, List<String> generatedColumns) {

    // Reuse one immutable empty column selection for plans that do not request named generated keys.
    private static final List<String> NO_COLUMNS = List.of();

    // Query preparation has no generated column selection, so one immutable plan can serve every query.
    private static final JdbcPreparationPlan QUERY = new JdbcPreparationPlan(ResultKind.QUERY, NO_COLUMNS);

    // Ordinary update preparation has no generated column selection, so one immutable plan can serve every update.
    private static final JdbcPreparationPlan UPDATE = new JdbcPreparationPlan(ResultKind.UPDATE, NO_COLUMNS);

    /**
     * Creates a preparation plan with an immutable generated column snapshot.
     *
     * @param resultKind expected result kind
     * @param generatedColumns generated columns
     */
    JdbcPreparationPlan {
        generatedColumns = List.copyOf(generatedColumns);
    }

    /**
     * Returns the shared query plan.
     *
     * @return query plan
     */
    static JdbcPreparationPlan query() {
        return QUERY;
    }

    /**
     * Returns the shared update plan.
     *
     * @return update plan
     */
    static JdbcPreparationPlan update() {
        return UPDATE;
    }

    /**
     * Creates a generated key plan and takes an immutable column snapshot.
     *
     * @param columnNames requested generated columns
     * @return generated key plan
     */
    static JdbcPreparationPlan generatedKeys(List<String> columnNames) {
        // Validate a stable snapshot instead of relying only on the mutable builder.
        List<String> copy = new ArrayList<>(columnNames);
        Set<String> unique = new HashSet<>(copy.size());
        for (String name : copy) {
            if (!unique.add(validateGeneratedColumn(name))) {
                throw new IllegalArgumentException("The generated column name '" + name + "' is duplicated.");
            }
        }
        return new JdbcPreparationPlan(ResultKind.GENERATED_KEYS, copy);
    }

    /**
     * Validates one generated column name.
     *
     * @param name generated column name
     * @return validated name for exact duplicate detection
     */
    static String validateGeneratedColumn(String name) {
        if (name.isBlank()) {
            throw new IllegalArgumentException("A generated column name must not be blank.");
        }
        return name;
    }

    /**
     * Primary result expected from JDBC.
     */
    enum ResultKind {
        QUERY,
        UPDATE,
        GENERATED_KEYS
    }
}
