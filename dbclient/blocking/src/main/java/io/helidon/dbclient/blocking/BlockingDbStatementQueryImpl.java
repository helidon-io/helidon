package io.helidon.dbclient.blocking;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbStatementQuery;
/**
 * Implementation of {@link BlockingDbStatementQuery}
 * <p>
 * {@inheritDoc}.
 */
public class BlockingDbStatementQueryImpl implements BlockingDbStatementQuery {

    private final DbStatementQuery dbStatementQuery;

    /**
     * Package private constructor.
     *
     * @param dbStatementQuery wrapper
     */
    BlockingDbStatementQueryImpl(DbStatementQuery dbStatementQuery) {
        this.dbStatementQuery = dbStatementQuery;
    }

    /**
     * {@inheritDoc}.
     *
     * @param parameters ordered parameters to set on this statement, never null
     * @return updated db statement
     */
    @Override
    public BlockingDbStatementQuery params(List<?> parameters) {
        return new BlockingDbStatementQueryImpl(dbStatementQuery.params(parameters));
    }

    /**
     * {@inheritDoc}.
     *
     * @param parameters ordered parameters to set on this statement
     * @return updated db statement
     */
    @Override
    public BlockingDbStatementQuery params(Object... parameters) {
        return new BlockingDbStatementQueryImpl(dbStatementQuery.params(parameters));
    }

    /**
     * {@inheritDoc}.
     *
     * @param parameters named parameters to set on this statement
     * @return updated db statement
     */
    @Override
    public BlockingDbStatementQuery params(Map<String, ?> parameters) {
        return new BlockingDbStatementQueryImpl(dbStatementQuery.params(parameters));
    }

    /**
     * {@inheritDoc}.
     *
     * @param parameters {@link Object} instance containing parameters
     * @return updated db statement
     */
    @Override
    public BlockingDbStatementQuery namedParam(Object parameters) {
        return new BlockingDbStatementQueryImpl(dbStatementQuery.namedParam(parameters));
    }

    /**
     * {@inheritDoc}.
     *
     * @param parameters {@link Object} instance containing parameters
     * @return updated db statement
     */
    @Override
    public BlockingDbStatementQuery indexedParam(Object parameters) {
        return new BlockingDbStatementQueryImpl(dbStatementQuery.indexedParam(parameters));
    }

    /**
     * {@inheritDoc}.
     *
     * @param parameter next parameter to set on this statement
     * @return updated db statement
     */
    @Override
    public BlockingDbStatementQuery addParam(Object parameter) {
        return new BlockingDbStatementQueryImpl(dbStatementQuery.addParam(parameter));
    }

    /**
     * {@inheritDoc}.
     *
     * @param name      name of parameter
     * @param parameter value of parameter
     * @return updated db statement
     */
    @Override
    public BlockingDbStatementQuery addParam(String name, Object parameter) {
        return new BlockingDbStatementQueryImpl(dbStatementQuery.addParam(name, parameter));
    }

    /**
     * {@inheritDoc}.
     *
     * @return The result of this statement, blocking.
     */
    @Override
    public Collection<DbRow> execute() {
        return dbStatementQuery.execute().collectList().await();
    }
}
