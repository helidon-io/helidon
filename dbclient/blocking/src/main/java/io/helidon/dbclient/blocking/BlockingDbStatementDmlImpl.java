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

import io.helidon.dbclient.DbStatementDml;

/**
 * Implementation of {@link BlockingDbStatementDml}.
 *
 * {@inheritDoc}
 */
public class BlockingDbStatementDmlImpl implements BlockingDbStatementDml {

    private DbStatementDml dbStatementDml;

    /**
     * Package private Constructor.
     *
     * @param dbStatementDml wrapper
     */
    BlockingDbStatementDmlImpl(DbStatementDml dbStatementDml) {
        this.dbStatementDml = dbStatementDml;
    }

    /**
     * {@inheritDoc}.
     *
     * @param parameters ordered parameters to set on this statement, never null
     * @return updated db statement
     */
    @Override
    public BlockingDbStatementDml params(List<?> parameters) {
        return new BlockingDbStatementDmlImpl(dbStatementDml.params(parameters));
    }

    /**
     * {@inheritDoc}.
     *
     * @param parameters ordered parameters to set on this statement
     * @return updated db statement
     */
    @Override
    public BlockingDbStatementDml params(Object... parameters) {
        return new BlockingDbStatementDmlImpl(dbStatementDml.params(parameters));
    }

    /**
     * {@inheritDoc}.
     *
     * @param parameters named parameters to set on this statement
     * @return updated db statement
     */
    @Override
    public BlockingDbStatementDml params(Map<String, ?> parameters) {
        return new BlockingDbStatementDmlImpl(dbStatementDml.params(parameters));
    }

    /**
     * {@inheritDoc}.
     *
     * @param parameters {@link Object} instance containing parameters
     * @return updated db statement
     */
    @Override
    public BlockingDbStatementDml namedParam(Object parameters) {
        return new BlockingDbStatementDmlImpl(dbStatementDml.namedParam(parameters));
    }

    /**
     * {@inheritDoc}.
     *
     * @param parameters {@link Object} instance containing parameters
     * @return updated db statement
     */
    @Override
    public BlockingDbStatementDml indexedParam(Object parameters) {
        return new BlockingDbStatementDmlImpl(dbStatementDml.indexedParam(parameters));
    }

    /**
     * {@inheritDoc}.
     *
     * @param parameter next parameter to set on this statement
     * @return updated db statement
     */
    @Override
    public BlockingDbStatementDml addParam(Object parameter) {
        return new BlockingDbStatementDmlImpl(dbStatementDml.addParam(parameter));
    }

    /**
     * {@inheritDoc}.
     *
     * @param name      name of parameter
     * @param parameter value of parameter
     * @return updated db statement
     */
    @Override
    public BlockingDbStatementDml addParam(String name, Object parameter) {
        return new BlockingDbStatementDmlImpl(dbStatementDml.addParam(name, parameter));
    }

    /**
     * {@inheritDoc}.
     *
     * @return The result of this statement, blocking.
     */
    @Override
    public Long execute() {
        return dbStatementDml.execute().await();
    }
}
