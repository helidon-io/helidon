/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.dbclient.blocking;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbStatementGet;
/**
 * Implementation of {@link BlockingDbStatementGet}.
 * <p>
 * {@inheritDoc}
 */
class BlockingDbStatementGetImpl implements BlockingDbStatementGet {
    private final DbStatementGet dbStatementGet;

    /**
     * Package private constructor.
     *
     * @param dbStatementGet wrapper
     */
    BlockingDbStatementGetImpl(DbStatementGet dbStatementGet) {
        this.dbStatementGet = dbStatementGet;
    }

    /**
     * {@inheritDoc}.
     *
     * @param parameters ordered parameters to set on this statement, never null
     * @return updated db statement
     */
    @Override
    public BlockingDbStatementGet params(List<?> parameters) {
        return new BlockingDbStatementGetImpl(dbStatementGet.params(parameters));
    }

    /**
     * {@inheritDoc}.
     *
     * @param parameters ordered parameters to set on this statement
     * @return updated db statement
     */
    @Override
    public BlockingDbStatementGet params(Object... parameters) {
        return new BlockingDbStatementGetImpl(dbStatementGet.params(parameters));
    }

    /**
     * {@inheritDoc}.
     *
     * @param parameters named parameters to set on this statement
     * @return updated db statement
     */
    @Override
    public BlockingDbStatementGet params(Map<String, ?> parameters) {
        return new BlockingDbStatementGetImpl(dbStatementGet.params(parameters));
    }

    /**
     * {@inheritDoc}.
     *
     * @param parameters {@link Object} instance containing parameters
     * @return updated db statement
     */
    @Override
    public BlockingDbStatementGet namedParam(Object parameters) {
        return new BlockingDbStatementGetImpl(dbStatementGet.namedParam(parameters));
    }

    /**
     * {@inheritDoc}.
     *
     * @param parameters {@link Object} instance containing parameters
     * @return updated db statement
     */
    @Override
    public BlockingDbStatementGet indexedParam(Object parameters) {
        return new BlockingDbStatementGetImpl(dbStatementGet.indexedParam(parameters));
    }

    /**
     * {@inheritDoc}.
     *
     * @param parameter next parameter to set on this statement
     * @return updated db statement
     */
    @Override
    public BlockingDbStatementGet addParam(Object parameter) {
        return new BlockingDbStatementGetImpl(dbStatementGet.addParam(parameter));
    }

    /**
     * {@inheritDoc}.
     *
     * @param name      name of parameter
     * @param parameter value of parameter
     * @return updated db statement
     */
    @Override
    public BlockingDbStatementGet addParam(String name, Object parameter) {
        return new BlockingDbStatementGetImpl(dbStatementGet.addParam(name, parameter));
    }

    /**
     * {@inheritDoc}.
     *
     * @return The result of this statement, blocking.
     */
    @Override
    public Optional<DbRow> execute() {
        return dbStatementGet.execute().await();
    }
}
