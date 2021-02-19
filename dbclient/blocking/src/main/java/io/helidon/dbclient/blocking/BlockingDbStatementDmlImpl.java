package io.helidon.dbclient.blocking;

import io.helidon.dbclient.DbStatementDml;

import java.util.List;
import java.util.Map;

public class BlockingDbStatementDmlImpl implements BlockingDbStatementDml {

    private DbStatementDml dbStatementDml;

    BlockingDbStatementDmlImpl(DbStatementDml dbStatementDml) {
        this.dbStatementDml = dbStatementDml;
    }

    @Override
    public BlockingDbStatementDml params(List<?> parameters) {
        return new BlockingDbStatementDmlImpl(dbStatementDml.params(parameters));
    }

    @Override
    public BlockingDbStatementDml params(Object... parameters) {
        return new BlockingDbStatementDmlImpl(dbStatementDml.params(parameters));
    }

    @Override
    public BlockingDbStatementDml params(Map<String, ?> parameters) {
        return new BlockingDbStatementDmlImpl(dbStatementDml.params(parameters));
    }

    @Override
    public BlockingDbStatementDml namedParam(Object parameters) {
        return new BlockingDbStatementDmlImpl(dbStatementDml.namedParam(parameters));
    }

    @Override
    public BlockingDbStatementDml indexedParam(Object parameters) {
        return new BlockingDbStatementDmlImpl(dbStatementDml.indexedParam(parameters));
    }

    @Override
    public BlockingDbStatementDml addParam(Object parameter) {
        return new BlockingDbStatementDmlImpl(dbStatementDml.addParam(parameter));
    }

    @Override
    public BlockingDbStatementDml addParam(String name, Object parameter) {
        return new BlockingDbStatementDmlImpl(dbStatementDml.addParam(name, parameter));
    }

    @Override
    public Long execute() {
        return dbStatementDml.execute().await();
    }
}
