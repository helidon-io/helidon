package io.helidon.dbclient.blocking;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbTransaction;

import java.util.function.Function;

class BlockingDbClientImpl implements BlockingDbClient {
    private final DbClient dbClient;

    private BlockingDbClientImpl(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public <T> T inTransaction(Function<DbTransaction, T> executor) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public <T> T execute(Function<BlockingDbExecute, T> executor) {
        return null;
    }

    @Override
    public String dbType() {
        return dbClient.dbType();
    }

    static BlockingDbClient create(Config config) {
        return new BlockingDbClientImpl(DbClient.builder(config).build());

    }

    static BlockingDbClient create(DbClient dbClient) {
        return new BlockingDbClientImpl(dbClient);
    }


}
