package io.helidon.dbclient.blocking;

import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbStatementGet;

import java.util.List;
import java.util.Map;
import java.util.Optional;

class BlockingDbStatementGetImpl implements BlockingDbStatementGet{
    private final DbStatementGet dbStatementGet;

    public BlockingDbStatementGetImpl(DbStatementGet dbStatementGet) {
        this.dbStatementGet = dbStatementGet;
    }


    @Override
    public BlockingDbStatementGet params(List<?> parameters) {
        return new BlockingDbStatementGetImpl(dbStatementGet.params(parameters));
    }

    @Override
    public BlockingDbStatementGet params(Object... parameters) {
        return new BlockingDbStatementGetImpl(dbStatementGet.params(parameters));
    }

    @Override
    public BlockingDbStatementGet params(Map<String, ?> parameters) {
        return new BlockingDbStatementGetImpl(dbStatementGet.params(parameters));
    }

    @Override
    public BlockingDbStatementGet namedParam(Object parameters) {
        return new BlockingDbStatementGetImpl(dbStatementGet.namedParam(parameters));
    }

    @Override
    public BlockingDbStatementGet indexedParam(Object parameters) {
        return new BlockingDbStatementGetImpl(dbStatementGet.indexedParam(parameters));
    }

    @Override
    public BlockingDbStatementGet addParam(Object parameter) {
        return new BlockingDbStatementGetImpl(dbStatementGet.addParam(parameter));
    }

    @Override
    public BlockingDbStatementGet addParam(String name, Object parameter) {
        return new BlockingDbStatementGetImpl(dbStatementGet.addParam(name, parameter));
    }

    @Override
    public Optional<DbRow> execute() {
        return dbStatementGet.execute().await();
    }
}
