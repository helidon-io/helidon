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

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.helidon.data.DataException;

/**
 * Result-column labels resolved once per result set.
 *
 * <p>Generated record mappers and application row mappers may address values
 * by label. Resolving metadata once on the first label lookup keeps subsequent
 * lookups out of the per-row hot path without penalizing index-only mapping.
 * This class uses only the result set's labels and names. It does not inspect
 * database primary-key metadata or infer object identity.</p>
 */
final class JdbcColumnLayout {

    // Zero cannot be a valid JDBC column index.
    private static final int AMBIGUOUS_INDEX = 0;

    private final int columnCount;

    // Retained only while the provider owns the result set.
    private final ResultSetMetaData metadata;

    // Carries safe operation details into failures raised during lazy metadata access.
    private final JdbcOperation operation;

    // Built on first label access so index-only mapping does not pay this cost.
    private Map<String, Integer> indexes;

    /**
     * Creates a cached column layout.
     *
     * @param columnCount physical column count
     * @param metadata result-set metadata
     * @param operation operation used for JDBC error translation
     */
    private JdbcColumnLayout(int columnCount, ResultSetMetaData metadata, JdbcOperation operation) {
        this.columnCount = columnCount;
        this.metadata = metadata;
        this.operation = operation;
    }

    /**
     * Reads the column count without resolving labels that an index-only mapper
     * does not use.
     *
     * <p>Column labels are preferred because SQL aliases define the mapping
     * contract. The physical column name is used only when a driver returns a
     * blank label. Label metadata is indexed lazily on the first label lookup,
     * so duplicate labels in unused columns do not affect scalar or index-only
     * mapping.</p>
     *
     * @param metadata result-set metadata
     * @param operation operation used for JDBC error translation
     * @return result-scoped layout
     */
    static JdbcColumnLayout create(ResultSetMetaData metadata, JdbcOperation operation) {
        try {
            return new JdbcColumnLayout(metadata.getColumnCount(), metadata, operation);
        } catch (SQLException e) {
            throw JdbcExceptionTranslator.translate(operation, e);
        }
    }

    /**
     * Returns the physical column count.
     *
     * @return column count
     */
    int columnCount() {
        return columnCount;
    }

    /**
     * Resolves a result label to its one-based JDBC index.
     *
     * @param label requested label
     * @return one-based column index
     * @throws DataException when the label is absent or ambiguous
     */
    int index(String label) {
        Integer index = indexes().get(label.toLowerCase(Locale.ROOT));
        if (index == null) {
            throw new DataException("The result does not contain a column labeled '" + label + "'.");
        }
        if (index == AMBIGUOUS_INDEX) {
            throw new DataException("The result contains more than one column labeled '" + label + "'.");
        }
        return index;
    }

    /**
     * Builds the case-insensitive label index once for this result set.
     *
     * <p>An ambiguous label is retained as an explicit sentinel. This permits
     * lookups of unrelated unique labels while ensuring that no caller can
     * depend on driver column order to resolve a duplicate.</p>
     *
     * @return normalized label index
     */
    private Map<String, Integer> indexes() {
        if (indexes == null) {
            indexes = createIndexes();
        }
        return indexes;
    }

    /**
     * Resolves labels and physical-name fallbacks from JDBC metadata.
     *
     * @return immutable normalized label index
     */
    private Map<String, Integer> createIndexes() {
        try {
            Map<String, Integer> resolved = new HashMap<>(Math.max(4, columnCount));
            for (int index = 1; index <= columnCount; index++) {
                String label = metadata.getColumnLabel(index);
                if (label == null || label.isBlank()) {
                    label = metadata.getColumnName(index);
                }
                if (label == null || label.isBlank()) {
                    throw new DataException("Result column " + index
                                                    + " has neither a usable label nor a column name.");
                }
                // Keep duplicate labels unusable without rejecting unrelated unique labels.
                resolved.merge(label.toLowerCase(Locale.ROOT),
                               index,
                               (existing, duplicate) -> AMBIGUOUS_INDEX);
            }
            return Map.copyOf(resolved);
        } catch (SQLException e) {
            throw JdbcExceptionTranslator.translate(operation, e);
        }
    }
}
