package io.helidon.nima.common.tls.spi;

import io.helidon.common.config.Config;
import io.helidon.common.config.ConfiguredProvider;
import io.helidon.nima.common.tls.TlsManager;

/**
 * {@link java.util.ServiceLoader} service provider for {@link io.helidon.nima.common.tls.TlsManager}.
 */
public interface TlsManagerProvider extends ConfiguredProvider<TlsManager> {

//    @Override
//    default TlsManager create(Config config, String name) {
//
//    }

}
