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
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable statement preparation and primary-result contract.
 */
final class JdbcPreparationPlan {

    /**
     * Primary result expected from JDBC.
     */
    enum ResultKind {
        /** Query result set. */
        QUERY,
        /** Update count. */
        UPDATE,
        /** Generated-key result set following an update. */
        GENERATED_KEYS
    }

    /** Shared empty generated-column list. */
    private static final List<String> NO_COLUMNS = List.of();
    /** Shared query preparation. */
    private static final JdbcPreparationPlan QUERY = new JdbcPreparationPlan(ResultKind.QUERY, NO_COLUMNS);
    /** Shared update preparation. */
    private static final JdbcPreparationPlan UPDATE = new JdbcPreparationPlan(ResultKind.UPDATE, NO_COLUMNS);

    /** Expected result kind. */
    private final ResultKind resultKind;
    /** Generated columns, empty for ordinary operations. */
    private final List<String> generatedColumns;

    /**
     * Creates a validated preparation plan.
     *
     * @param resultKind expected result kind
     * @param generatedColumns generated columns
     */
    private JdbcPreparationPlan(ResultKind resultKind, List<String> generatedColumns) {
        this.resultKind = resultKind;
        this.generatedColumns = generatedColumns;
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
     * Creates a generated-key plan and takes an immutable column snapshot.
     *
     * @param columnNames requested generated columns
     * @return generated-key plan
     */
    static JdbcPreparationPlan generatedKeys(List<String> columnNames) {
        Objects.requireNonNull(columnNames, "Generated column names must not be null");
        List<String> copy = new ArrayList<>(columnNames);
        Set<String> unique = new HashSet<>(copy.size());
        for (int index = 0; index < copy.size(); index++) {
            String name = copy.get(index);
            if (!unique.add(validateGeneratedColumn(name, index))) {
                throw new IllegalArgumentException("Duplicate generated column name: " + name);
            }
        }
        return new JdbcPreparationPlan(ResultKind.GENERATED_KEYS, List.copyOf(copy));
    }

    /**
     * Validates and normalizes one generated column name.
     *
     * @param name generated column name
     * @param index zero-based column index
     * @return normalized name for duplicate detection
     */
    static String validateGeneratedColumn(String name, int index) {
        Objects.requireNonNull(name, "Generated column name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Generated column name must not be blank at index " + index);
        }
        return name.toLowerCase(Locale.ROOT);
    }

    /**
     * Returns the expected result kind.
     *
     * @return result kind
     */
    ResultKind resultKind() {
        return resultKind;
    }

    /**
     * Returns the immutable plan's generated columns.
     *
     * @return generated columns
     */
    List<String> generatedColumns() {
        return generatedColumns;
    }
}
