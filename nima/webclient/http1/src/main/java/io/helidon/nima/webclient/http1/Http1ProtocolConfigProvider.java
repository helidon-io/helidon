package io.helidon.nima.webclient.http1;

import io.helidon.common.config.Config;
import io.helidon.nima.webclient.spi.ProtocolConfigProvider;

/**
 * Implementation of protocol config provider.
 */
public class Http1ProtocolConfigProvider implements ProtocolConfigProvider<Http1ClientProtocolConfig> {
    /**
     * Required to be used by {@link java.util.ServiceLoader}.
     * @deprecated do not use directly, use Http1ClientProtocol
     */
    public Http1ProtocolConfigProvider() {
    }

    @Override
    public String configKey() {
        return Http1ProtocolProvider.CONFIG_KEY;
    }

    @Override
    public Http1ClientProtocolConfig create(Config config, String name) {
        return Http1ClientProtocolConfig.builder()
                .config(config)
                .name(name);
    }
}
