package io.helidon.nima.webclient.spi;

import io.helidon.common.config.ConfiguredProvider;

/**
 * Client protocol configuration.
 *
 * @param <T> type of configuration supported by this provider
 */
public interface ProtocolConfigProvider<T extends ProtocolConfig> extends ConfiguredProvider<T> {
}
