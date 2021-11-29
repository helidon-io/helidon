package io.helidon.microprofile.rsocket.client;


import jakarta.enterprise.context.ApplicationScoped;

import java.lang.annotation.Annotation;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.config.ConfigValue;
import io.helidon.config.mp.MpConfig;
import io.helidon.rsocket.client.RSocketClient;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import org.eclipse.microprofile.config.ConfigProvider;


/**
 * Producer for RSocket clients.
 */
@ApplicationScoped
public class RSocketClientProducer {

    /**
     * Produce RSocket Client.
     * @param ip InjectionPoint
     * @return RSocketClient
     */
    @Dependent
    @Produces
    @CustomRSocket
    public RSocketClient produceCustomRSocketClient(InjectionPoint ip) {

        Set<Annotation> qualifiers = ip.getQualifiers();
        for (Annotation qualifier : qualifiers) {
            if (qualifier.annotationType().equals(CustomRSocket.class)) {
                CustomRSocket rsc = (CustomRSocket) qualifier;
                if (rsc.value().isBlank()) {
                    return createClient(null);
                }
                return createClient(rsc.value());
            }
        }

        org.eclipse.microprofile.config.Config config = ConfigProvider.getConfig();
        Config helidonConfig = MpConfig.toHelidonConfig(config).get("rsocket");

        ConfigValue<RSocketClient> configValue = helidonConfig.as(RSocketClient::create);
        if (configValue.isPresent()) {
            return configValue.get();
        }
        throw new RuntimeException("Unable to configure RSocket client!");
    }

    /**
     * Default RSocket client producer.
     * @return RSocketClient
     */
    @Produces
    public RSocketClient produceDefaultRSocketClient() {
        return createClient(null);
    }

    private RSocketClient createClient(String prefix) {
        org.eclipse.microprofile.config.Config config = ConfigProvider.getConfig();
        Config helidonConfig;
        if (prefix == null || prefix.isEmpty()) {
            helidonConfig = MpConfig.toHelidonConfig(config).get("rsocket");
        } else {
            helidonConfig = MpConfig.toHelidonConfig(config).get("rsocket." + prefix);
        }
        ConfigValue<RSocketClient> configValue = helidonConfig.as(RSocketClient::create);
        if (configValue.isPresent()) {
            return configValue.get();
        }
        throw new RuntimeException("Unable to configure RSocket client!");
    }
}


