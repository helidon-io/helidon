package io.helidon.dbclient.blocking;

import io.helidon.dbclient.DbExecute;

class BlockingDbExecuteImpl implements BlockingDbExecute {
    private final DbExecute dbExecute;

    BlockingDbExecuteImpl(DbExecute dbExecute) {
        this.dbExecute = dbExecute;
    }

    @Override
    public BlockingDbStatementQuery createNamedQuery(String statementName, String statement) {
        return BlockingDbStatementQuery.create(dbExecute.createNamedQuery(statementName, statement));
    }

    @Override
    public BlockingDbStatementQuery createNamedQuery(String statementName) {
        return BlockingDbStatementQuery.create(dbExecute.createNamedQuery(statementName));
    }

    @Override
    public BlockingDbStatementQuery createQuery(String statement) {
        return BlockingDbStatementQuery.create(dbExecute.createQuery(statement));
    }

    @Override
    public BlockingDbStatementGet createNamedGet(String statementName, String statement) {
        return BlockingDbStatementGet.create(dbExecute.createNamedGet(statementName,statement));
    }

    @Override
    public BlockingDbStatementGet createNamedGet(String statementName) {
        return BlockingDbStatementGet.create(dbExecute.createNamedGet(statementName));
    }

    @Override
    public BlockingDbStatementGet createGet(String statement) {
        return BlockingDbStatementGet.create(dbExecute.createGet(statement));
    }

    @Override
    public BlockingDbStatementDml createNamedInsert(String statementName) {
        return BlockingDbStatementDml.create(dbExecute.createNamedInsert(statementName));
    }

    @Override
    public BlockingDbStatementDml createInsert(String statement) {
        return BlockingDbStatementDml.create(dbExecute.createInsert(statement));
    }

    @Override
    public BlockingDbStatementDml createNamedUpdate(String statementName) {
        return BlockingDbStatementDml.create(dbExecute.createNamedUpdate(statementName));
    }

    @Override
    public BlockingDbStatementDml createUpdate(String statement) {
        return BlockingDbStatementDml.create(dbExecute.createUpdate(statement));
    }

    @Override
    public BlockingDbStatementDml createNamedDelete(String statementName) {
        return BlockingDbStatementDml.create(dbExecute.createNamedDelete(statementName));
    }

    @Override
    public BlockingDbStatementDml createDelete(String statement) {
        return BlockingDbStatementDml.create(dbExecute.createDelete(statement));
    }

    @Override
    public BlockingDbStatementDml createNamedDmlStatement(String statementName, String statement) {
        return BlockingDbStatementDml.create(dbExecute.createNamedDmlStatement(statementName, statement));
    }

    @Override
    public BlockingDbStatementDml createNamedDmlStatement(String statementName) {
        return BlockingDbStatementDml.create(dbExecute.createNamedDmlStatement(statementName));
    }

    @Override
    public BlockingDbStatementDml createDmlStatement(String statement) {
        return BlockingDbStatementDml.create(dbExecute.createDmlStatement(statement));
    }
}
