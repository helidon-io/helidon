/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.dbclient.jdbc;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import io.helidon.dbclient.DbStatement;
import io.helidon.dbclient.common.CommonStatement;

abstract class JdbcStatement<S extends DbStatement<S>> extends CommonStatement<S> {

    // JDBC statement execution context
    private final StatementContext context;

    // Statement preparation handler.
    // Instance is initialized to PrepareInitial instance (parameter type was not chosen yet).
    // It's updated to PrepareIndex or PrepareName depending on 1st parameter being set to the statement.
    private Params params;

    JdbcStatement(StatementContext context) {
        super(context.clientContext());
        this.context = context;
        this.params = new StatementParams(context, this::updatePrepare);
    }

    @Override
    public S params(List<?> parameters) {
        parameters.forEach(this::addParam);
        return identity();
    }

    @Override
    public S params(Map<String, ?> parameters) {
        parameters.forEach(this::addParam);
        return identity();
    }

    @Override
    public S addParam(Object parameter) {
        params.indexed().addParam(new ParameterValueHandler.ObjectHandler(parameter));
        return identity();
    }

    @Override
    public S addParam(boolean parameter) {
        params.indexed().addParam(new ParameterValueHandler.BooleanHandler(parameter));
        return identity();
    }

    @Override
    public S addParam(String parameter) {
        params.indexed().addParam(new ParameterValueHandler.StringHandler(parameter));
        return identity();
    }

    @Override
    public S addParam(byte parameter) {
        params.indexed().addParam(new ParameterValueHandler.ByteHandler(parameter));
        return identity();
    }

    @Override
    public S addParam(short parameter) {
        params.indexed().addParam(new ParameterValueHandler.ShortHandler(parameter));
        return identity();
    }

    @Override
    public S addParam(int parameter) {
        params.indexed().addParam(new ParameterValueHandler.IntHandler(parameter));
        return identity();
    }

    @Override
    public S addParam(long parameter) {
        params.indexed().addParam(new ParameterValueHandler.LongHandler(parameter));
        return identity();
    }

    @Override
    public S addParam(float parameter) {
        params.indexed().addParam(new ParameterValueHandler.FloatHandler(parameter));
        return identity();
    }

    @Override
    public S addParam(double parameter) {
        params.indexed().addParam(new ParameterValueHandler.DoubleHandler(parameter));
        return identity();
    }

    @Override
    public S addParam(BigInteger parameter) {
        params.indexed().addParam(new ParameterValueHandler.BigDecimalHandler(new BigDecimal(parameter)));
        return identity();
    }

    @Override
    public S addParam(BigDecimal parameter) {
        params.indexed().addParam(new ParameterValueHandler.BigDecimalHandler(parameter));
        return identity();
    }

    @Override
    public S addParam(byte[] parameter) {
        params.indexed().addParam(new ParameterValueHandler.BytesHandler(parameter));
        return identity();
    }

    @Override
    public S addParam(String name, Object parameter) {
        params.named().addParam(name, new ParameterValueHandler.ObjectHandler(parameter));
        return identity();
    }

    @Override
    public S addParam(String name, boolean parameter) {
        params.named().addParam(name, new ParameterValueHandler.BooleanHandler(parameter));
        return identity();
    }

    @Override
    public S addParam(String name, String parameter) {
        params.named().addParam(name, new ParameterValueHandler.StringHandler(parameter));
        return identity();
    }

    @Override
    public S addParam(String name, byte parameter) {
        params.named().addParam(name, new ParameterValueHandler.ByteHandler(parameter));
        return identity();
    }

    @Override
    public S addParam(String name, short parameter) {
        params.named().addParam(name, new ParameterValueHandler.ShortHandler(parameter));
        return identity();
    }

    @Override
    public S addParam(String name, int parameter) {
        params.named().addParam(name, new ParameterValueHandler.IntHandler(parameter));
        return identity();
    }

    @Override
    public S addParam(String name, long parameter) {
        params.named().addParam(name, new ParameterValueHandler.LongHandler(parameter));
        return identity();
    }

    @Override
    public S addParam(String name, float parameter) {
        params.named().addParam(name, new ParameterValueHandler.FloatHandler(parameter));
        return identity();
    }

    @Override
    public S addParam(String name, double parameter) {
        params.named().addParam(name, new ParameterValueHandler.DoubleHandler(parameter));
        return identity();
    }

    @Override
    public S addParam(String name, BigInteger parameter) {
        params.named().addParam(name, new ParameterValueHandler.BigDecimalHandler(new BigDecimal(parameter)));
        return identity();
    }

    @Override
    public S addParam(String name, BigDecimal parameter) {
        params.named().addParam(name, new ParameterValueHandler.BigDecimalHandler(parameter));
        return identity();
    }

    @Override
    public S addParam(String name, byte[] parameter) {
        params.named().addParam(name, new ParameterValueHandler.BytesHandler(parameter));
        return identity();
    }

    StatementContext context() {
        return context;
    }

    // Current statement preparation handler.
    Params prepare() {
        return params;
    }

    // Callback method passed to StatementParams Builder instance.
    // This method is called from PrepareInitial instance to update Builder instance to the next state.
    // This next state handles only indexed or named parameters.
    private void updatePrepare(Params prepare) {
        if (this.params.state() != Params.State.INIT) {
            throw new IllegalStateException("Cannot update statement preparation method when method was already chosen.");
        }
        this.params = prepare;
    }

    // Internal JDBC statement parameters preparation handlers
    //
    // Parameters type is initialized with 1st statement parameter being set
    // * as StatementIndexedParams for indexed parameter
    // * as StatementNamedParams for named parameter.
    // Each subsequent parameter must be of the same type (indexed or named).

    // Builder interface to prepare statement for execution and execute it
    abstract static class Params {

        abstract StatementIndexedParams indexed();

        abstract StatementNamedParams named();

        abstract Statement createStatement(Connection connection) throws SQLException;

        abstract long executeUpdate() throws SQLException;

        abstract ResultSet executeQuery() throws SQLException;

        abstract Params.State state();

        abstract JdbcClientServiceContext createServiceContext();

        // Parameters preparation state
        enum State {
            // Initial state: parameters type was not chosen yet
            INIT,
            // Indexed parameters
            INDEXED,
            // Named parameters
            NAMED
        }

    }

}
