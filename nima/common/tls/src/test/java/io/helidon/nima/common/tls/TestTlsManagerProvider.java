package io.helidon.nima.common.tls;

import io.helidon.common.config.Config;
import io.helidon.nima.common.tls.spi.TlsManagerProvider;

public class TestTlsManagerProvider implements TlsManagerProvider {

    @Override
    public String configKey() {
        return "test";
    }

    @Override
    public TlsManager create(Config config, String name) {
        CustomTestTlsManagerConfig cfg = CustomTestTlsManagerConfig.create(config);
        return new TestTlsManager(cfg);
    }

}
