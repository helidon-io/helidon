package io.helidon.nima.common.tls;

import java.util.Map;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.nima.common.tls.spi.TlsManagerProvider;

import org.junit.jupiter.api.Test;

class TlsManagerTest {

    @Test
    void tlsManager() {
        Config config = Config.create(ConfigSources.create(
                Map.of("test.schedule", "123",
                       "test.uri", "http://localhost")));
        TlsManagerProvider provider = HelidonServiceLoader.builder(ServiceLoader.load(TlsManagerProvider.class))
//                .addService(new ConfigBasedResourceProvider())
                .build()
                .asList()
                .iterator()
                .next();
        TlsManager tlsManager = provider.create(config.get(provider.configKey()), "@default");
        System.out.println(tlsManager);
    }
}
