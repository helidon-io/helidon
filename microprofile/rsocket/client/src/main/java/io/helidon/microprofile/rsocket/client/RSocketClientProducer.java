/*
 * Copyright (c) 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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


