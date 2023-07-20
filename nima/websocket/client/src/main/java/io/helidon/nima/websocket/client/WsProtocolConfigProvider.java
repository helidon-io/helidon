package io.helidon.nima.websocket.client;

import io.helidon.common.config.Config;
import io.helidon.nima.webclient.spi.ProtocolConfigProvider;

/**
 * Implementation of protocol config provider.
 */
public class WsProtocolConfigProvider implements ProtocolConfigProvider<WsClientProtocolConfig> {
    /**
     * Required to be used by {@link java.util.ServiceLoader}.
     *
     * @deprecated do not use directly, use WsClientProtocolConfig
     */
    public WsProtocolConfigProvider() {
    }

    @Override
    public String configKey() {
        return WsProtocolProvider.CONFIG_KEY;
    }

    @Override
    public WsClientProtocolConfig create(Config config, String name) {
        return WsClientProtocolConfig.builder()
                .config(config)
                .name(name);
    }
}
