/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
