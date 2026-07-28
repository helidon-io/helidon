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

import java.util.List;
import java.util.Optional;

/**
 * Materialized cardinality stage for a mapped JDBC result.
 *
 * @param <T> mapped type
 */
final class JdbcRows<T> implements JdbcClient.Rows<T> {

    private final JdbcStatement statement;
    private final JdbcClient.RowMapper<T> mapper;
    private final JdbcPreparationPlan plan;

    // Distinguishes a nullable scalar terminal from ordinary row mapping.
    private final Class<T> optionalScalarType;

    /**
     * Creates a result stage without acquiring JDBC resources.
     *
     * @param statement owning statement
     * @param mapper row mapper
     * @param plan preparation plan
     */
    JdbcRows(JdbcStatement statement, JdbcClient.RowMapper<T> mapper, JdbcPreparationPlan plan) {
        this(statement, mapper, plan, null);
    }

    /**
     * Creates a scalar result stage whose optional terminal maps SQL
     * {@code NULL} to {@link Optional#empty()}.
     *
     * @param statement owning statement
     * @param mapper required scalar mapper used by {@code one} and {@code list}
     * @param plan preparation plan
     * @param optionalScalarType scalar type used by the optional terminal
     */
    JdbcRows(JdbcStatement statement,
             JdbcClient.RowMapper<T> mapper,
             JdbcPreparationPlan plan,
             Class<T> optionalScalarType) {
        this.statement = statement;
        this.mapper = mapper;
        this.plan = plan;
        this.optionalScalarType = optionalScalarType;
    }

    /**
     * Executes with exactly-one cardinality.
     *
     * @return mapped value
     */
    @Override
    public T one() {
        return statement.one(mapper, plan);
    }

    /**
     * Executes with zero-or-one cardinality.
     *
     * @return optional mapped value
     */
    @Override
    public Optional<T> optional() {
        return optionalScalarType == null
                ? statement.optional(mapper, plan)
                : statement.optionalScalar(optionalScalarType, plan);
    }

    /**
     * Executes and materializes every row.
     *
     * @return mapped values in encounter order
     */
    @Override
    public List<T> list() {
        return statement.list(mapper, plan);
    }
}
