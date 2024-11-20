/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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
package io.helidon.dbclient;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static io.helidon.dbclient.DbStatementParameters.UNDEFINED;

/**
 * Base {@link DbStatement} implementation.
 *
 * @param <S> type of subclass
 */
public abstract class DbStatementBase<S extends DbStatement<S>> implements DbStatement<S> {

    private final DbExecuteContext context;
    private DbStatementParameters parameters;

    /**
     * Create a new instance.
     *
     * @param context context
     */
    protected DbStatementBase(DbExecuteContext context) {
        this.context = context;
    }

    /**
     * Get the execution context.
     *
     * @return execution context
     */
    public DbExecuteContext context() {
        return context;
    }

    /**
     * Returns execution context cast to it's extending class.
     *
     * @param cls {@link DbExecuteContext} extending class
     * @return extended execution context
     * @param <C> execution context extending type
     */
    protected <C extends DbExecuteContext> C context(Class<C> cls) {
        return cls.cast(context);
    }

    /**
     * Get the statement parameters.
     *
     * @return statement parameters
     */
    public DbStatementParameters parameters() {
        return parameters != null ? parameters : UNDEFINED;
    }

    /**
     * Get the statement type.
     *
     * @return statement type
     */
    public abstract DbStatementType statementType();

    /**
     * Execute the statement with interception.
     *
     * @param function function used to compute the query result
     * @param <T>      query result type
     * @return query result
     */
    protected <T> T doExecute(BiFunction<CompletableFuture<Long>, DbClientServiceContext, T> function) {
        CompletableFuture<Void> stmtFuture = new CompletableFuture<>();
        CompletableFuture<Long> queryFuture = new CompletableFuture<>();
        queryFuture.whenComplete((r, ex) -> stmtFuture.complete(null));
        DbClientServiceContext serviceContext =
                new DbClientServiceContextImpl(context, statementType(), stmtFuture, queryFuture, parameters);
        context().clientServices().forEach(service -> service.statement(serviceContext));
        return function.apply(queryFuture, serviceContext);
    }

    /**
     * Decorate the given stream to invoke {@link Stream#close()} on terminal operations.
     *
     * @param stream stream to decorate
     * @param <T>    the type of the stream elements
     * @return decorated stream
     */
    protected static <T> Stream<T> autoClose(Stream<T> stream) {
        return AutoClosingStream.decorate(stream);
    }

    @Override
    public S namedParam(Object parameters) {
        Objects.requireNonNull(parameters, "Missing instance containing parameters");
        @SuppressWarnings("unchecked")
        Class<Object> theClass = (Class<Object>) parameters.getClass();
        params(context.dbMapperManager().toNamedParameters(parameters, theClass));
        return identity();
    }

    @Override
    public S indexedParam(Object parameters) {
        Objects.requireNonNull(parameters, "Missing instance containing parameters");
        @SuppressWarnings("unchecked")
        Class<Object> theClass = (Class<Object>) parameters.getClass();
        params(context.dbMapperManager().toIndexedParameters(parameters, theClass));
        return identity();
    }

    @Override
    public S indexedParam(Object parameters, String... names) {
        Objects.requireNonNull(parameters, "Missing instance containing parameters");
        Objects.requireNonNull(names, "Missing parameter names");
        if (names.length == 0) {
            throw new IllegalArgumentException("Missing parameter names");
        }
        @SuppressWarnings("unchecked")
        Class<Object> theClass = (Class<Object>) parameters.getClass();
        Map<String, ?> namedParameters = context.dbMapperManager().toNamedParameters(parameters, theClass);
        List<Object> indexedParameters = new ArrayList<>(names.length);
        for (String name : names) {
            indexedParameters.add(namedParameters.get(name));
        }
        params(Collections.unmodifiableList(indexedParameters));
        return identity();
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
        if (parameters == null) {
            parameters = new DbIndexedStatementParameters();
        }
        parameters.addParam(parameter);
        return identity();
    }

    @Override
    public S addParam(String name, Object parameter) {
        if (parameters == null) {
            parameters = new DbNamedStatementParameters();
        }
        parameters.addParam(name, parameter);
        return identity();
    }

    @Override
    public S addParam(boolean parameter) {
        return addParam((Boolean) parameter);
    }

    @Override
    public S addParam(String parameter) {
        return addParam((Object) parameter);
    }

    @Override
    public S addParam(byte parameter) {
        return addParam((Object) parameter);
    }

    @Override
    public S addParam(short parameter) {
        return addParam((Object) parameter);
    }

    @Override
    public S addParam(int parameter) {
        return addParam((Object) parameter);
    }

    @Override
    public S addParam(long parameter) {
        return addParam((Object) parameter);
    }

    @Override
    public S addParam(float parameter) {
        return addParam((Object) parameter);
    }

    @Override
    public S addParam(double parameter) {
        return addParam((Object) parameter);
    }

    @Override
    public S addParam(BigInteger parameter) {
        return addParam((Object) parameter);
    }

    @Override
    public S addParam(BigDecimal parameter) {
        return addParam((Object) parameter);
    }

    @Override
    public S addParam(byte[] parameter) {
        return addParam((Object) parameter);
    }

    @Override
    public S addParam(String name, boolean parameter) {
        return addParam(name, (Object) parameter);
    }

    @Override
    public S addParam(String name, String parameter) {
        return addParam(name, (Object) parameter);
    }

    @Override
    public S addParam(String name, byte parameter) {
        return addParam(name, (Object) parameter);
    }

    @Override
    public S addParam(String name, short parameter) {
        return addParam(name, (Object) parameter);
    }

    @Override
    public S addParam(String name, int parameter) {
        return addParam(name, (Object) parameter);
    }

    @Override
    public S addParam(String name, long parameter) {
        return addParam(name, (Object) parameter);
    }

    @Override
    public S addParam(String name, float parameter) {
        return addParam(name, (Object) parameter);
    }

    @Override
    public S addParam(String name, double parameter) {
        return addParam(name, (Object) parameter);
    }

    @Override
    public S addParam(String name, BigInteger parameter) {
        return addParam(name, (Object) parameter);
    }

    @Override
    public S addParam(String name, BigDecimal parameter) {
        return addParam(name, (Object) parameter);
    }

    @Override
    public S addParam(String name, byte[] parameter) {
        return addParam(name, (Object) parameter);
    }

    /**
     * Get this instance as the correct type.
     *
     * @return this instance typed to correct type
     */
    @SuppressWarnings("unchecked")
    protected S identity() {
        return (S) this;
    }

}
