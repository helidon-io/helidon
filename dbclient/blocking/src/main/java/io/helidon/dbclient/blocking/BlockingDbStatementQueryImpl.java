package io.helidon.dbclient.blocking;

import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbStatementQuery;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class BlockingDbStatementQueryImpl implements BlockingDbStatementQuery{
    private final DbStatementQuery dbStatementQuery;

    BlockingDbStatementQueryImpl(DbStatementQuery dbStatementQuery) {
        this.dbStatementQuery = dbStatementQuery;
    }


    @Override
    public BlockingDbStatementQuery params(List<?> parameters) {
        return new BlockingDbStatementQueryImpl(dbStatementQuery.params(parameters));
    }

    @Override
    public BlockingDbStatementQuery params(Object... parameters) {
        return new BlockingDbStatementQueryImpl(dbStatementQuery.params(parameters));
    }

    @Override
    public BlockingDbStatementQuery params(Map<String, ?> parameters) {
        return new BlockingDbStatementQueryImpl(dbStatementQuery.params(parameters));
    }

    @Override
    public BlockingDbStatementQuery namedParam(Object parameters) {
        return new BlockingDbStatementQueryImpl(dbStatementQuery.namedParam(parameters));
    }

    @Override
    public BlockingDbStatementQuery indexedParam(Object parameters) {
        return new BlockingDbStatementQueryImpl(dbStatementQuery.indexedParam(parameters));
    }

    @Override
    public BlockingDbStatementQuery addParam(Object parameter) {
        return new BlockingDbStatementQueryImpl(dbStatementQuery.addParam(parameter));
    }

    @Override
    public BlockingDbStatementQuery addParam(String name, Object parameter) {
        return new BlockingDbStatementQueryImpl(dbStatementQuery.addParam(name,parameter));
    }

    @Override
    public Collection<DbRow> execute() {
        return dbStatementQuery.execute().collectList().await();
    }
}
