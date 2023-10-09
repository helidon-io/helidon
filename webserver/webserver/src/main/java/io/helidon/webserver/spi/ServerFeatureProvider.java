package io.helidon.webserver.spi;

import io.helidon.common.config.ConfiguredProvider;

/**
 * Server features provider is a {@link java.util.ServiceLoader} provider API to discover server wide features.
 */
public interface ServerFeatureProvider<T extends ServerFeature> extends ConfiguredProvider<T> {
}
