package io.helidon.microprofile.rsocket.client;

import io.helidon.config.Config;
import io.helidon.config.ConfigValue;
import io.helidon.config.mp.MpConfig;
import io.helidon.rsocket.client.RSocketClient;
import org.eclipse.microprofile.config.ConfigProvider;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Extension;

/**
 * RSocket client CDI Extension.
 */
public class RSocketClientCDIExtension implements Extension {

    private static final String RSOCKET_CONFIG_NAME_PREFIX = "rsocket";

    void afterBeanDiscovery(@Observes AfterBeanDiscovery addEvent) {
        addEvent.addBean()
                .types(io.helidon.rsocket.client.RSocketClient.class)
                .qualifiers(Default.Literal.INSTANCE, Any.Literal.INSTANCE)
                .scope(ApplicationScoped.class)
                .name(io.helidon.rsocket.client.RSocketClient.class.getName())
                .beanClass(io.helidon.rsocket.client.RSocketClient.class)
                .createWith(creationContext -> {
                    org.eclipse.microprofile.config.Config config = ConfigProvider.getConfig();
                    Config helidonConfig = MpConfig.toHelidonConfig(config).get(RSOCKET_CONFIG_NAME_PREFIX);

                    ConfigValue<RSocketClient> configValue = helidonConfig.as(RSocketClient::create);
                    if (configValue.isPresent()) {
                        return configValue.get();
                    }
                    throw new RuntimeException("Unable to configure RSocket client!");
                });
    }
}
