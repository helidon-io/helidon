package io.helidon.nima.common.tls;

import java.util.function.Consumer;

class TestTlsManager implements TlsManager {

    final CustomTestTlsManagerConfig tlsManagerConfig;

    TestTlsManager(CustomTestTlsManagerConfig tlsManagerConfig) {
        this.tlsManagerConfig = tlsManagerConfig;
    }

    @Override
    public TlsManagerConfig prototype() {
        return null;
    }

    @Override
    public String name() {
        return null;
    }

    @Override
    public String type() {
        return null;
    }

    @Override
    public void reload() {

    }

    @Override
    public void register(Consumer<Tls> tlsConsumer) {

    }

    @Override
    public Tls tls() {
        return null;
    }

}
