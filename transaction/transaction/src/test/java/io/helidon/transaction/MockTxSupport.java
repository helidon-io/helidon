package io.helidon.transaction;

import java.util.concurrent.Callable;

import io.helidon.service.registry.Service;

@Service.Singleton
class MockTxSupport implements TxSupport {

    private Tx.Type type;

    MockTxSupport() {
        this.type = null;
    }

    @Override
    public <T> T transaction(Tx.Type type, Callable<T> task) {
        this.type = type;
        return null;
    }

    Tx.Type type() {
        return type;
    }

}
