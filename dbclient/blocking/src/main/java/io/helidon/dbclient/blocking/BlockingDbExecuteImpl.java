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

import io.helidon.dbclient.DbExecute;

/**
 * Implementation of {@link BlockingDbExecute).
 * <p>
 * {@inheritDoc}
 */
class BlockingDbExecuteImpl implements BlockingDbExecute {
    private final DbExecute dbExecute;

    /**
     * Package private constructor.
     *
     * @param dbExecute wrapper
     */
    BlockingDbExecuteImpl(DbExecute dbExecute) {
        this.dbExecute = dbExecute;
    }

    /**
     * {@inheritDoc}.
     *
     * @param statementName the name of the statement
     * @param statement     the query statement
     * @return data modification statement
     */
    @Override
    public BlockingDbStatementQuery createNamedQuery(String statementName, String statement) {
        return BlockingDbStatementQuery.create(dbExecute.createNamedQuery(statementName, statement));
    }

    /**
     * {@inheritDoc}.
     *
     * @param statementName the name of the configuration node with statement
     * @return data modification statement
     */
    @Override
    public BlockingDbStatementQuery createNamedQuery(String statementName) {
        return BlockingDbStatementQuery.create(dbExecute.createNamedQuery(statementName));
    }

    /**
     * {@inheritDoc}.
     *
     * @param statement the query statement to be executed
     * @return data modification statement
     */
    @Override
    public BlockingDbStatementQuery createQuery(String statement) {
        return BlockingDbStatementQuery.create(dbExecute.createQuery(statement));
    }

    /**
     * {@inheritDoc}.
     *
     * @param statementName the name of the statement
     * @param statement     the statement text
     * @return data modification statement
     */
    @Override
    public BlockingDbStatementGet createNamedGet(String statementName, String statement) {
        return BlockingDbStatementGet.create(dbExecute.createNamedGet(statementName, statement));
    }

    /**
     * {@inheritDoc}.
     *
     * @param statementName the name of the configuration node with statement
     * @return data modification statement
     */
    @Override
    public BlockingDbStatementGet createNamedGet(String statementName) {
        return BlockingDbStatementGet.create(dbExecute.createNamedGet(statementName));
    }

    /**
     * {@inheritDoc}.
     *
     * @param statement the query statement to be executed
     * @return data modification statement
     */
    @Override
    public BlockingDbStatementGet createGet(String statement) {
        return BlockingDbStatementGet.create(dbExecute.createGet(statement));
    }

    /**
     * {@inheritDoc}.
     *
     * @param statementName the name of the statement
     * @return data modification statement
     */
    @Override
    public BlockingDbStatementDml createNamedInsert(String statementName) {
        return BlockingDbStatementDml.create(dbExecute.createNamedInsert(statementName));
    }

    /**
     * {@inheritDoc}.
     *
     * @param statement the statement text
     * @return data modification statement
     */
    @Override
    public BlockingDbStatementDml createInsert(String statement) {
        return BlockingDbStatementDml.create(dbExecute.createInsert(statement));
    }

    /**
     * {@inheritDoc}.
     *
     * @param statementName the name of the statement
     * @return data modification statement
     */
    @Override
    public BlockingDbStatementDml createNamedUpdate(String statementName) {
        return BlockingDbStatementDml.create(dbExecute.createNamedUpdate(statementName));
    }

    /**
     * {@inheritDoc}.
     *
     * @param statement the statement text
     * @return data modification statement
     */
    @Override
    public BlockingDbStatementDml createUpdate(String statement) {
        return BlockingDbStatementDml.create(dbExecute.createUpdate(statement));
    }

    /**
     * {@inheritDoc}.
     *
     * @param statementName the name of the statement
     * @return data modification statement
     */
    @Override
    public BlockingDbStatementDml createNamedDelete(String statementName) {
        return BlockingDbStatementDml.create(dbExecute.createNamedDelete(statementName));
    }

    /**
     * {@inheritDoc}.
     *
     * @param statement the statement text
     * @return data modification statement
     */
    @Override
    public BlockingDbStatementDml createDelete(String statement) {
        return BlockingDbStatementDml.create(dbExecute.createDelete(statement));
    }

    /**
     * {@inheritDoc}.
     *
     * @param statementName the name of the statement
     * @param statement     the statement text
     * @return data modification statement
     */
    @Override
    public BlockingDbStatementDml createNamedDmlStatement(String statementName, String statement) {
        return BlockingDbStatementDml.create(dbExecute.createNamedDmlStatement(statementName, statement));
    }

    /**
     * {@inheritDoc}.
     *
     * @param statementName the name of the configuration node with statement
     * @return data modification statement
     */
    @Override
    public BlockingDbStatementDml createNamedDmlStatement(String statementName) {
        return BlockingDbStatementDml.create(dbExecute.createNamedDmlStatement(statementName));
    }

    /**
     * {@inheritDoc}.
     *
     * @param statement the data modification statement to be executed
     * @return data modification statement
     */
    @Override
    public BlockingDbStatementDml createDmlStatement(String statement) {
        return BlockingDbStatementDml.create(dbExecute.createDmlStatement(statement));
    }
}
